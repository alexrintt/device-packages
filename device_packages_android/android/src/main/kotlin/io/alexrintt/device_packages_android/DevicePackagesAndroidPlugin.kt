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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import java.io.File

const val DEFAULT_INCLUDE_SYSTEM_PACKAGES: Boolean = false
const val DEFAULT_INCLUDE_ICON: Boolean = false
const val DEFAULT_ONLY_OPENABLE_PACKAGES: Boolean = false

const val NO_PACKAGE_METADATA: Int = 0x0
const val GET_PACKAGE_METADATA_FLAG: Int = PackageManager.GET_META_DATA
const val SYSTEM_APP_FLAG: Int =
  ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

const val MISSING_PERMISSION_TO_REQUEST_INSTALL_PACKAGE_ERROR: String =
  "MissingPermissionToRequestInstallPackage"

const val PACKAGE_NOT_FOUND_EXCEPTION: String = "PackageNotFoundException"
const val UNSUCCESSFUL_PACKAGE_INSTALL_REQUEST_EXCEPTION: String =
  "UnsuccessfulPackageInstallRequestException"
const val INVALID_INSTALLER_EXCEPTION: String = "InvalidInstallerException"
const val PACKAGE_IS_NOT_OPENABLE_EXCEPTION: String =
  "PackageIsNotOpenableException"


const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"

/** DevicePackagesAndroidPlugin */
class DevicePackagesAndroidPlugin : FlutterPlugin {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private lateinit var packageEventChannels: EventChannel
  private lateinit var getInstalledPackagesEventChannel: EventChannel

  lateinit var context: Context

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

    packageEventChannels = EventChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.eventchannel/packages"
    )
    packageEventChannels.setStreamHandler(PackagesStreamHandler(this))

    getInstalledPackagesEventChannel = EventChannel(
      flutterPluginBinding.binaryMessenger,
      "io.alexrintt.device_packages.platform_interface.eventchannel/getinstalledpackages"
    )
    getInstalledPackagesEventChannel.setStreamHandler(
      GetInstalledPackagesStreamHandler(this)
    )
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)

    packageEventChannels.setStreamHandler(null)
    getInstalledPackagesEventChannel.setStreamHandler(null)
  }
}

class GetInstalledPackagesStreamHandler(private val plugin: DevicePackagesAndroidPlugin) :
  EventChannel.StreamHandler {
  private var eventSink: EventChannel.EventSink? = null

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    if (events == null || arguments == null) return

    registerListener(events)

    val includeSystemPackages: Boolean =
      parseArg(arguments, "includeSystemPackages")
        ?: DEFAULT_INCLUDE_SYSTEM_PACKAGES
    val includeIcon: Boolean =
      parseArg(arguments, "includeIcon") ?: DEFAULT_INCLUDE_ICON
    val onlyOpenablePackages: Boolean =
      parseArg(arguments, "onlyOpenablePackages")
        ?: DEFAULT_ONLY_OPENABLE_PACKAGES

    val flags: Int = PackageManager.GET_META_DATA

    val packages: List<PackageInfo> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        plugin.context.packageManager!!.getInstalledPackages(
          PackageManager.PackageInfoFlags.of(
            flags.toLong()
          )
        )
      } else {
        @Suppress("DEPRECATION") // we are handling when API >= Build.VERSION_CODES.TIRAMISU.
        plugin.context.packageManager!!.getInstalledPackages(flags)
      }

    packages.forEach skip@{
      val isSystemPackage =
        (it.applicationInfo?.flags ?: 0) and SYSTEM_APP_FLAG != 0

      if (isSystemPackage && !includeSystemPackages) {
        return@skip
      }

      if (onlyOpenablePackages) {
        if (plugin.context.packageManager.getLaunchIntentForPackage(it.packageName) == null) {
          return@skip
        }
      }

      if (eventSink == null) {
        // Listener was canceled during the loop by some other event.
        return cancelListener()
      }

      eventSink?.success(it.toMap(plugin.context, includeIcon))
    }

    cancelListener()
  }

  override fun onCancel(arguments: Any?) = cancelListener()

  private fun registerListener(eventSink: EventChannel.EventSink) {
    cancelListener()
    this.eventSink = eventSink
  }

  private fun cancelListener() {
    if (eventSink != null) {
      eventSink!!.endOfStream()
      eventSink = null
    }
  }
}

fun PackageInfo.toMap(
  context: Context? = null,
  includeIcon: Boolean = false
): Map<String, *> {
  assert(if (includeIcon) context != null else true) { "Context must not be null if [includeIcon] is [true]." }

  val isOpenable: (PackageManager) -> Boolean = { packageManager ->
    packageManager.getLaunchIntentForPackage(this.packageName) != null
  }

  return mapOf(
    "id" to this.packageName,
    "name" to context?.packageManager?.getApplicationLabel(this.applicationInfo),
    "installerPath" to this.applicationInfo.sourceDir,
    "isSystemPackage" to (this.applicationInfo.flags and SYSTEM_APP_FLAG != 0),
    "isOpenable" to (context?.packageManager?.let(isOpenable)),
    "icon" to
      if (includeIcon) {
        bitmapToBytes(
          drawableToBitmap(
            context!!.packageManager.getApplicationIcon(
              this.applicationInfo
            )
          )
        )
      } else {
        null
      }
  )
}

class DevicePackagesAndroidPluginMethodCallHandler(private val plugin: DevicePackagesAndroidPlugin) :
  MethodCallHandler {
  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getInstalledPackages" -> getInstalledPackages(call, result)
      "getInstalledPackageCount" -> getInstalledPackageCount(call, result)
      "getPackage" -> getPackage(call, result)
      "isPackageInstalled" -> isPackageInstalled(call, result)
      "installPackage" -> installPackage(call, result)
      "uninstallPackage" -> uninstallPackage(call, result)
      "openPackageSettings" -> openPackageSettings(call, result)
      "openPackage" -> openPackage(call, result)
      else -> result.notImplemented()
    }
  }

  private fun openPackage(call: MethodCall, result: Result) {
    val packageName: String = parseArg<String>(call.arguments, "packageId")!!

    val launchIntent: Intent =
      plugin.context.packageManager.getLaunchIntentForPackage(packageName)
        ?: return result.error(
          PACKAGE_IS_NOT_OPENABLE_EXCEPTION,
          "The package [$packageName] has no launch intent, so it's not possible to open.",
          call.arguments,
        )

    plugin.context.startActivity(launchIntent)
  }

  private fun openPackageSettings(call: MethodCall, result: Result) {
    openPackageSettings(parseArg<String>(call.arguments, "packageId")!!)
    result.success(null)
  }

  private fun openPackageSettings(packageName: String) {
    val appSettingsIntent: Intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    plugin.context.startActivity(appSettingsIntent)
  }

  private fun uninstallPackage(packageName: String) {
    val appUninstallIntent = Intent(Intent.ACTION_DELETE).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    plugin.context.startActivity(appUninstallIntent)
  }

  private fun uninstallPackage(call: MethodCall, result: Result) {
    uninstallPackage(parseArg<String>(call.arguments, "packageId")!!)
    result.success(null)
  }

  private fun installPackage(call: MethodCall, result: Result) {
    val installerUri: String? = parseArg<String>(call.arguments, "installerUri")
    val installerPath: String? =
      parseArg<String>(call.arguments, "installerPath")

    assert(installerUri != null || installerPath != null) { "You must set [installerUri] or [installerPath] when calling [installPackage]" }

    val apkUri =
      if (installerUri != null)
        Uri.parse(installerUri)
      else Uri.fromFile(
        File(installerPath!!)
      )

    val type: String? = plugin.context.contentResolver.getType(apkUri)
    val isApk: Boolean = type == APK_MIME_TYPE

    if (!isApk) {
      return result.error(
        INVALID_INSTALLER_EXCEPTION,
        "When calling [installPackage] on Android, the [installPath] or [installerUri] must be a valid address to an apk file.",
        call.arguments
      )
    }

    try {
      installPackage(apkUri)
      result.success(null)
    } catch (e: SecurityException) {
      return result.error(
        UNSUCCESSFUL_PACKAGE_INSTALL_REQUEST_EXCEPTION,
        "Missing read permissions for uri $apkUri of type $type to request install.",
        call.arguments
      )
    }
  }

  private fun installPackage(uri: Uri) {
    val installPackageIntent =
      Intent(Intent.ACTION_VIEW).apply {
        val flags: Int =
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        setDataAndType(uri, APK_MIME_TYPE)
        setFlags(flags)
      }

    plugin.context.startActivity(installPackageIntent)
  }

  private fun canRequestPackageInstalls(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      plugin.context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  private fun getPackage(
    packageName: String,
    flags: Int = NO_PACKAGE_METADATA
  ): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      plugin.context.packageManager.getPackageInfo(
        packageName,
        PackageManager.PackageInfoFlags.of(flags.toLong())
      )
    } else {
      @Suppress("DEPRECATION")
      plugin.context.packageManager.getPackageInfo(
        packageName,
        flags
      )
    }
  }

  private fun getPackageOrNull(
    packageName: String,
    flags: Int = NO_PACKAGE_METADATA
  ): PackageInfo? {
    return try {
      getPackage(packageName, flags)
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }
  }

  private fun isPackageInstalled(packageName: String): Boolean =
    getPackageOrNull(packageName) != null

  private fun isPackageInstalled(call: MethodCall, result: Result) {
    val packageName: String = parseArg<String>(call.arguments, "packageId")!!
    result.success(isPackageInstalled(packageName))
  }

  private fun getPackage(call: MethodCall, result: Result) {
    val includeIcon: Boolean =
      parseArg(call.arguments, "includeIcon") ?: DEFAULT_INCLUDE_ICON
    val packageName: String = parseArg(call.arguments, "packageId")!!

    return try {
      val packageInfo: PackageInfo = getPackage(packageName)
      result.success(packageInfo.toMap(plugin.context, includeIcon))
    } catch (e: PackageManager.NameNotFoundException) {
      result.error(
        PACKAGE_NOT_FOUND_EXCEPTION,
        "The package $packageName was not found, check if you have permissions to access it.",
        call.arguments
      )
    }
  }

  private fun getInstalledPackages(call: MethodCall, result: Result) {
    val includeSystemPackages: Boolean =
      parseArg(call.arguments, "includeSystemPackages")
        ?: DEFAULT_INCLUDE_SYSTEM_PACKAGES
    val includeIcon: Boolean =
      parseArg(call.arguments, "includeIcon") ?: DEFAULT_INCLUDE_ICON
    val onlyOpenablePackages: Boolean =
      parseArg(call.arguments, "onlyOpenablePackages")
        ?: DEFAULT_ONLY_OPENABLE_PACKAGES

    val packages: List<PackageInfo> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        plugin.context.packageManager!!.getInstalledPackages(
          PackageManager.PackageInfoFlags.of(
            GET_PACKAGE_METADATA_FLAG.toLong()
          )
        )
      } else {
        @Suppress("DEPRECATION") // we are handling when API >= Build.VERSION_CODES.TIRAMISU.
        plugin.context.packageManager!!.getInstalledPackages(
          GET_PACKAGE_METADATA_FLAG
        )
      }

    val eligiblePackages = packages.filter isEligible@{
      val isSystemPackage =
        (it.applicationInfo?.flags ?: 0) and SYSTEM_APP_FLAG != 0

      if (isSystemPackage && !includeSystemPackages) {
        return@isEligible false
      }

      if (onlyOpenablePackages) {
        if (plugin.context.packageManager.getLaunchIntentForPackage(it.packageName) == null) {
          return@isEligible false
        }
      }

      return@isEligible true
    }.map { it.toMap(plugin.context, includeIcon) }

    result.success(eligiblePackages)
  }

  private fun getInstalledPackageCount(call: MethodCall, result: Result) {
    val includeSystemPackages: Boolean =
      parseArg(call.arguments, "includeSystemPackages")
        ?: DEFAULT_INCLUDE_SYSTEM_PACKAGES
    val onlyOpenablePackages: Boolean =
      parseArg(call.arguments, "onlyOpenablePackages")
        ?: DEFAULT_ONLY_OPENABLE_PACKAGES

    val packages: List<PackageInfo> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        plugin.context.packageManager!!.getInstalledPackages(
          PackageManager.PackageInfoFlags.of(
            NO_PACKAGE_METADATA.toLong()
          )
        )
      } else {
        @Suppress("DEPRECATION") // we are handling when API >= Build.VERSION_CODES.TIRAMISU.
        plugin.context.packageManager!!.getInstalledPackages(NO_PACKAGE_METADATA)
      }

    val eligiblePackages = packages.filter isEligible@{
      val isSystemPackage =
        (it.applicationInfo?.flags ?: 0) and SYSTEM_APP_FLAG != 0

      if (isSystemPackage && !includeSystemPackages) {
        return@isEligible false
      }

      if (onlyOpenablePackages) {
        if (plugin.context.packageManager.getLaunchIntentForPackage(it.packageName) == null) {
          return@isEligible false
        }
      }

      return@isEligible true
    }

    result.success(eligiblePackages.size)
  }
}

class PackagesStreamHandler(private val plugin: DevicePackagesAndroidPlugin) :
  EventChannel.StreamHandler {
  private var receiver: BroadcastReceiver? = null
  private var eventSink: EventChannel.EventSink? = null

  private val hasListener: Boolean get() = receiver != null || eventSink != null

  override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {
    if (eventSink == null) return

    if (hasListener) cancelListener()

    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        val packageName: String? = intent.data?.encodedSchemeSpecificPart
        val action: String? = when (intent.action) {
          Intent.ACTION_PACKAGE_ADDED -> "install"
          Intent.ACTION_PACKAGE_REMOVED -> "uninstall"
          Intent.ACTION_PACKAGE_REPLACED -> "update"
          else -> null
        }

        if (action == null || packageName == null) return

        eventSink.success(
          mapOf(
            "packageId" to packageName,
            "action" to action
          )
        )
      }
    }

    registerListener(receiver, eventSink)

    val intentFilter = IntentFilter().apply {
      addAction(Intent.ACTION_PACKAGE_ADDED)
      addAction(Intent.ACTION_PACKAGE_REMOVED)
      addAction(Intent.ACTION_PACKAGE_REPLACED)
      addDataScheme("package")
    }

    plugin.context.registerReceiver(receiver, intentFilter)
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
      plugin.context.unregisterReceiver(receiver)
      receiver = null
    }
  }
}

private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
  val byteArrayOutputStream = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
  return byteArrayOutputStream.toByteArray()
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
  if (drawable is BitmapDrawable) {
    val bitmapDrawable: BitmapDrawable = drawable
    if (bitmapDrawable.bitmap != null) {
      return bitmapDrawable.bitmap
    }
  }
  val bitmap: Bitmap =
    if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
      Bitmap.createBitmap(
        1,
        1,
        Bitmap.Config.ARGB_8888
      ) // Single color bitmap will be created of 1x1 pixel
    } else {
      Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
      )
    }
  val canvas = Canvas(bitmap)
  drawable.setBounds(0, 0, canvas.width, canvas.height)
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
