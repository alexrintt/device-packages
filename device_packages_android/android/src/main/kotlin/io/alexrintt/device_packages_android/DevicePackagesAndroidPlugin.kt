package io.alexrintt.device_packages_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** DevicePackagesAndroidPlugin */
class DevicePackagesAndroidPlugin : FlutterPlugin {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel

  var context: Context? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext

    channel = MethodChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.default/methodchannel"
    )
    channel.setMethodCallHandler(
      DevicePackagesAndroidPluginMethodCallHandler(
        this
      )
    )

    eventChannel = EventChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.default/eventchannel"
    )
    eventChannel.setStreamHandler(DevicePackagesAndroidPluginStreamHandler(this))
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    context = null
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
  }
}


class DevicePackagesAndroidPluginMethodCallHandler(val plugin: DevicePackagesAndroidPlugin) :
  MethodCallHandler {
  override fun onMethodCall(
    @NonNull call: MethodCall,
    @NonNull result: Result
  ) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else {
      result.notImplemented()
    }
  }
}

class DevicePackagesAndroidPluginStreamHandler(private val plugin: DevicePackagesAndroidPlugin) :
  EventChannel.StreamHandler {
  private var listeners: MutableMap<String, EventChannel.EventSink> =
    mutableMapOf()
  private var receivers: MutableMap<String, BroadcastReceiver> = mutableMapOf()

  private fun deserializeEventArguments(arguments: Any?): Pair<String?, String?> {
    if (arguments !is Map<*, *>) return Pair<String?, String?>(null, null)

    val args: Map<*, *> = arguments

    val listenerId: String? = args["listenerId"] as String?
    val eventName: String? = args["eventName"] as String?

    return Pair(eventName, listenerId)
  }

  override fun onListen(arguments: Any, events: EventChannel.EventSink) {
    if (arguments !is Map<*, *>) return

    val (eventName, listenerId) = deserializeEventArguments(arguments)

    if (eventName == null || listenerId == null) {
      events.error(
        MISSING_EVENT_NAME_OR_LISTENER_ID_CODE,
        MISSING_EVENT_NAME_OR_LISTENER_ID,
        arguments,
      )
      return events.endOfStream()
    }

    cancelListener(listenerId)

    when (eventName) {
      "didInstallPackage" -> didInstallPackage(listenerId, events, arguments)
    }
  }

  override fun onCancel(arguments: Any?) {
    val (_, listenerId) = deserializeEventArguments(arguments)
    if (listenerId != null) {
      cancelListener(listenerId)
    }
  }

  private fun registerListener(
    listenerId: String,
    broadcastReceiver: BroadcastReceiver,
    eventSink: EventChannel.EventSink,
    cancelIfExists: Boolean = true
  ) {
    if (cancelIfExists) {
      cancelListener(listenerId)
    }

    if (listeners[listenerId] == null) {
      listeners[listenerId] = eventSink
      receivers[listenerId] = broadcastReceiver
    }
  }

  private fun cancelListener(listenerId: String) {
    if (listeners[listenerId] != null) {
      listeners[listenerId]!!.endOfStream()
      listeners.remove(listenerId)
    }

    if (receivers[listenerId] != null) {
      // Unregister the BroadcastReceiver instance.
      plugin.context?.unregisterReceiver(receivers[listenerId])?.apply {
        // Remove receiver from map only if context is not null.
        receivers[listenerId]
      }
    }
  }

  private fun didInstallPackage(
    listenerId: String,
    eventSink: EventChannel.EventSink,
    arguments: Any?
  ) {
    val receiver: BroadcastReceiver =
      DidInstallPackageBroadcastReceiver(eventSink)

    registerListener(listenerId, receiver, eventSink)

    if (plugin.context == null) {
      eventSink.error(
        TRIED_CALLING_EVENT_CHANNEL_WHEN_CONTEXT_IS_NULL_CODE,
        TRIED_CALLING_EVENT_CHANNEL_WHEN_CONTEXT_IS_NULL,
        arguments
      )
      return cancelListener(listenerId)
    }

    val intentFilter = IntentFilter().apply {
      addAction(Intent.ACTION_PACKAGE_ADDED)
      //addAction(Intent.ACTION_PACKAGE_REMOVED)
      addDataScheme("package")
    }

    // intentFilter.priority = 999

    plugin.context!!.registerReceiver(receiver, intentFilter)
  }
}

const val MISSING_EVENT_NAME_OR_LISTENER_ID_CODE =
  "MISSING_EVENT_NAME_OR_LISTENER_ID"
const val MISSING_EVENT_NAME_OR_LISTENER_ID =
  "An event API was called with [listenerId] or [eventName] set to null."

const val TRIED_CALLING_EVENT_CHANNEL_WHEN_CONTEXT_IS_NULL_CODE: String =
  "TRIED_CALLING_EVENT_CHANNEL_WHEN_CONTEXT_IS_NULL"
const val TRIED_CALLING_EVENT_CHANNEL_WHEN_CONTEXT_IS_NULL: String =
  "Tried to call [events] (eventSink) on native call when [context] (application context of type Context) is null."

class DidInstallPackageBroadcastReceiver(val eventSink: EventChannel.EventSink) :
  BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent == null) return

    val action: String? = intent.getAction()
    val packageName: String? = intent.getData()?.getEncodedSchemeSpecificPart()

    if (action != Intent.ACTION_PACKAGE_ADDED) {
      Log.e(
        "WARNING",
        "[DidInstallPackageBroadcastReceiver] received an intent that is not [ACTION_PACKAGE_ADDED], this is probably being used in the wrong intent builder."
      )
      return
    }

    eventSink.success(mapOf("name" to packageName))

    Log.d("DID_INSTALL_PACKAGE", "Received a new event.")
  }
}