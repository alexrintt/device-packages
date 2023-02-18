package io.alexrintt.device_packages_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
  private lateinit var installEventChannel: EventChannel
  private lateinit var uninstallEventChannel: EventChannel

  var context: Context? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext

    channel = MethodChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.methodchannel/default"
    )
    channel.setMethodCallHandler(
      DevicePackagesAndroidPluginMethodCallHandler(
        this
      )
    )

    installEventChannel = EventChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.eventchannel/install"
    )
    installEventChannel.setStreamHandler(DidInstallPackageStreamHandler(this))

    uninstallEventChannel = EventChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.eventchannel/uninstall"
    )
    uninstallEventChannel.setStreamHandler(DidUninstallPackageStreamHandler(this))
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    context = null
    channel.setMethodCallHandler(null)
    uninstallEventChannel.setStreamHandler(null)
  }
}

class DevicePackagesAndroidPluginMethodCallHandler(private val plugin: DevicePackagesAndroidPlugin) :
  MethodCallHandler {
  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getDevicePackages" -> getDevicePackages(call, result)
      "getDevicePackageCount" -> getDevicePackageCount(call, result)
      else -> result.notImplemented()
    }
  }

  companion object {
    const val APPLICATION_CONTEXT_IS_NULL: String =
      "APPLICATION_CONTEXT_IS_NULL"
    const val APPLICATION_PACKAGE_MANAGER_IS_NULL: String =
      "APPLICATION_CONTEXT_IS_NULL"
  }

  private fun getDevicePackages(call: MethodCall, result: Result) {
    val includeSystemPackages: Boolean =
      parseArg(call.arguments, "includeSystemPackages") ?: false
    val includeIcon: Boolean =
      parseArg(call.arguments, "includeIcon") ?: true

    if (plugin.context == null) {
      return result.error(
        APPLICATION_CONTEXT_IS_NULL,
        "Could not fetch device packages, context is [null].",
        call.arguments
      )
    }

    if (plugin.context!!.packageManager == null) {
      return result.error(
        APPLICATION_PACKAGE_MANAGER_IS_NULL,
        "Could not fetch device packages, the package manager is [null].",
        call.arguments
      )
    }

    val flags: Int = PackageManager.GET_META_DATA

    val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
      plugin.context!!.packageManager!!.getInstalledPackages(
        PackageManager.PackageInfoFlags.of(
          flags.toLong()
        )
      )
    } else {
      @Suppress("DEPRECATION") // we are handling when API >= 33.
      plugin.context!!.packageManager!!.getInstalledPackages(flags)
    }

    val eligiblePackages = packages.filter isEligible@{
      val isSystemPackage =
        (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

      if (isSystemPackage && !includeSystemPackages) {
        return@isEligible false
      }

      return@isEligible true
    }.map {
      mapOf(
        "id" to it.packageName,
        "name" to plugin.context!!.packageManager.getApplicationLabel(it.applicationInfo),
        "path" to it.applicationInfo.sourceDir,
        "icon" to
          if (includeIcon) {
            bitmapToBytes(
              drawableToBitmap(
                plugin.context!!.packageManager.getApplicationIcon(
                  it.applicationInfo
                )
              )
            )
          } else {
            null
          }
      )
    }

    result.success(eligiblePackages)
  }

  private fun getDevicePackageCount(call: MethodCall, result: Result) {
    val includeSystemPackages: Boolean =
      parseArg(call.arguments, "includeSystemPackages") ?: false

    if (plugin.context == null) {
      return result.error(
        APPLICATION_CONTEXT_IS_NULL,
        "Could not fetch device packages, context is [null].",
        call.arguments
      )
    }

    if (plugin.context!!.packageManager == null) {
      return result.error(
        APPLICATION_PACKAGE_MANAGER_IS_NULL,
        "Could not fetch device packages, the package manager is [null].",
        call.arguments
      )
    }

    val flags = 0

    val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
      plugin.context!!.packageManager!!.getInstalledPackages(
        PackageManager.PackageInfoFlags.of(
          flags.toLong()
        )
      )
    } else {
      @Suppress("DEPRECATION") // we are handling when API >= 33.
      plugin.context!!.packageManager!!.getInstalledPackages(flags)
    }

    val eligiblePackages = packages.filter isEligible@{
      val isSystemPackage =
        (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

      if (isSystemPackage && !includeSystemPackages) {
        return@isEligible false
      }

      return@isEligible true
    }

    result.success(eligiblePackages.size)
  }
}

abstract class DidChangePackageListStreamHandler(
  private val plugin: DevicePackagesAndroidPlugin,
  private val action: String
) :
  EventChannel.StreamHandler {
  private var receiver: BroadcastReceiver? = null
  private var eventSink: EventChannel.EventSink? = null

  private val hasListener: Boolean get() = receiver != null || eventSink != null

  override fun onListen(arguments: Any, eventSink: EventChannel.EventSink) {
    if (hasListener) cancelListener()

    val includeSystemPackages: Boolean =
      parseArg(arguments, "includeSystemPackages") ?: false
    val includeIcon: Boolean = parseArg(arguments, "includeIcon") ?: true

    val receiver: BroadcastReceiver =
      DidChangePackageListBroadcastReceiver(
        eventSink,
        includeSystemPackages = includeSystemPackages,
        includeIcon = includeIcon,
        action = action
      )

    registerListener(receiver, eventSink)

    if (plugin.context == null) {
      Log.d(
        "DEVICE_PACKAGES",
        "Plugin application context is null when calling $action event handler, ignoring."
      )
      return cancelListener()
    }

    val intentFilter = IntentFilter().apply {
      addAction(action)
      addDataScheme("package")
    }

    plugin.context!!.registerReceiver(receiver, intentFilter)
  }

  override fun onCancel(arguments: Any?) = cancelListener()

  private fun registerListener(
    receiver: BroadcastReceiver,
    eventSink: EventChannel.EventSink
  ) {
    cancelListener()

    this.eventSink = eventSink
    this.receiver = receiver
  }

  private fun cancelListener() {
    if (eventSink != null) {
      eventSink!!.endOfStream()
      eventSink = null
    }

    if (receiver != null) {
      plugin.context?.unregisterReceiver(receiver)
    }
  }
}

class DidInstallPackageStreamHandler(plugin: DevicePackagesAndroidPlugin) :
  DidChangePackageListStreamHandler(plugin, Intent.ACTION_PACKAGE_ADDED)

class DidUninstallPackageStreamHandler(plugin: DevicePackagesAndroidPlugin) :
  DidChangePackageListStreamHandler(plugin, Intent.ACTION_PACKAGE_REMOVED)

class DidChangePackageListBroadcastReceiver(
  private val eventSink: EventChannel.EventSink,
  private val includeSystemPackages: Boolean,
  private val includeIcon: Boolean,
  private val action: String
) :
  BroadcastReceiver() {

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

    val packageInfo: PackageInfo? = try {
      if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
          packageName,
          PackageManager.PackageInfoFlags.of(flags.toLong())
        )
      } else {
        @Suppress("DEPRECATION") // we are handling when API >= 33.
        context.packageManager.getPackageInfo(packageName, flags)
      }
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }

    val isSystemPackage =
      (packageInfo?.applicationInfo?.flags
        ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

    if (isSystemPackage && !includeSystemPackages) return

    val icon: ByteArray? = try {
      if (includeIcon)
        context.packageManager.getApplicationIcon(packageName).let {
          bitmapToBytes(drawableToBitmap(it))
        }
      else
        null
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }

    eventSink.success(
      mapOf(
        "id" to (packageInfo?.packageName ?: packageName),
        "name" to packageInfo?.let {
          context.packageManager.getApplicationLabel(
            it.applicationInfo
          )
        },
        "path" to packageInfo?.applicationInfo?.sourceDir,
        "icon" to icon
      )
    )
  }

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

private inline fun <reified T> parseArg(arguments: Any?, key: String): T? {
  if (arguments !is Map<*, *>) return null

  if (arguments[key] !is T) {
    return null
  }

  return arguments[key] as T
}