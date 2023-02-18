package io.alexrintt.device_packages_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream

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


class DevicePackagesAndroidPluginMethodCallHandler(private val plugin: DevicePackagesAndroidPlugin) :
  MethodCallHandler {
  override fun onMethodCall(
    @NonNull call: MethodCall,
    @NonNull result: Result
  ) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${Build.VERSION.RELEASE}")
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
      "didUninstallPackage" -> didUninstallPackage(
        listenerId,
        events,
        arguments
      )
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

  private inline fun <reified T> parseArg(arguments: Any?, key: String): T? {
    if (arguments !is Map<*, *>) return null

    if (arguments[key] !is T) {
      return null
    }

    return arguments[key] as T
  }

  private fun didInstallPackage(
    listenerId: String,
    eventSink: EventChannel.EventSink,
    arguments: Any?
  ) {
    val includeSystemPackages: Boolean =
      parseArg(arguments, "includeSystemPackages") ?: false
    val includeIcon: Boolean = parseArg(arguments, "includeIcon") ?: true

    val action: String = Intent.ACTION_PACKAGE_ADDED

    val receiver: BroadcastReceiver =
      DidChangePackageListBroadcastReceiver(
        eventSink,
        includeSystemPackages = includeSystemPackages,
        includeIcon = includeIcon,
        action = action
      )

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
      addAction(action)
      addDataScheme("package")
    }

    plugin.context!!.registerReceiver(receiver, intentFilter)
  }

  private fun didUninstallPackage(
    listenerId: String,
    eventSink: EventChannel.EventSink,
    arguments: Any?
  ) {
    val includeSystemPackages: Boolean =
      parseArg(arguments, "includeSystemPackages") ?: false
    val includeIcon: Boolean = parseArg(arguments, "includeIcon") ?: true

    val action: String = Intent.ACTION_PACKAGE_REMOVED

    val receiver: BroadcastReceiver =
      DidChangePackageListBroadcastReceiver(
        eventSink,
        includeSystemPackages = includeSystemPackages,
        includeIcon = includeIcon,
        action = action
      )

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
      addAction(action)
      addDataScheme("package")
    }

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

class DidChangePackageListBroadcastReceiver(
  private val eventSink: EventChannel.EventSink,
  private val includeSystemPackages: Boolean,
  private val includeIcon: Boolean,
  private val action: String
) :
  BroadcastReceiver() {

  @SuppressWarnings("deprecated")
  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent == null || context == null) return

    val sourceAction: String? = intent.action
    val packageName: String? = intent.data?.encodedSchemeSpecificPart

    if (sourceAction == null || packageName == null) return

    if (sourceAction != action) {
      Log.e(
        "WARNING",
        "[DidChangePackageListBroadcastReceiver] received an intent that is not [$action], this is probably being used in the wrong intent builder."
      )
      return
    }

    val flags: Int = PackageManager.GET_META_DATA

    val applicationInfo: ApplicationInfo =
      if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getApplicationInfo(
          packageName,
          PackageManager.ApplicationInfoFlags.of(flags.toLong())
        )
      } else {
        @Suppress("DEPRECATION") // we are handling when API >= 33.
        context.packageManager.getApplicationInfo(packageName, flags)
      }

    val isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    if (isSystem && !includeSystemPackages) return

    val icon: ByteArray? = if (!includeIcon) null else
      context.packageManager.getApplicationIcon(packageName).let {
        bitmapToBytes(drawableToBitmap(it))
      }

    eventSink.success(
      mapOf(
        "id" to applicationInfo.packageName,
        "name" to applicationInfo.name,
        "path" to applicationInfo.sourceDir,
        "icon" to icon
      )
    )
  }

  private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
  }

  private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val bitmap: Bitmap = Bitmap
      .createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.RGB_565
      )

    val canvas = Canvas(bitmap)
    drawable.setBounds(
      0, 0, drawable.intrinsicWidth,
      drawable.intrinsicHeight
    )
    drawable.draw(canvas)
    return bitmap
  }
}