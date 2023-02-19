import 'dart:io';

import 'package:device_packages_platform_interface/device_packages_platform_interface.dart';

export 'package:device_packages_platform_interface/device_packages_platform_interface.dart'
    show PackageInfo, PackageEvent, PackageAction;
export 'package:device_packages_platform_interface/device_packages_platform_interface.dart'
    show DevicePackagesPlatformInterface;
export 'package:device_packages_platform_interface/device_packages_platform_interface_method_channel.dart'
    show
        InvalidInstallerException,
        MissingPermissionToRequestInstallPackageError,
        UnsuccessfulPackageInstallRequestException,
        PackageNotFoundException;

class DevicePackages {
  /// Returns all packages from the device, note that this may cause several
  /// performance issues if called on the main UI thread.
  ///
  /// Since this is a [Future], this cannot be used in a lazy load. You do not have any
  /// feedback until all packages are loaded; again, this can take several seconds
  /// on devices with more than 100+ packages (which is common on real Android devices).
  ///
  /// It is recommended to use [getInstalledPackagesAsStream] instead.
  ///
  /// - If [includeIcon] is true, it will return the [PackageInfo] with [icon] set to a non-null value representing the icon bitmap bytes.
  /// - If [includeSystemPackages] is true, it will return internal packages (if supported in the host platform).
  static Future<List<PackageInfo>> getInstalledPackages({
    bool includeIcon = true,
    bool includeSystemPackages = true,
  }) =>
      DevicePackagesPlatformInterface.instance.getInstalledPackages(
        includeIcon: includeIcon,
        includeSystemPackages: includeSystemPackages,
      );

  /// Returns a future that completes with the device total package count.
  ///
  /// Useful to show progress of a loading indicator.
  static Future<int> getInstalledPackageCount({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) =>
      DevicePackagesPlatformInterface.instance.getInstalledPackageCount(
        includeSystemPackages: includeSystemPackages,
      );

  /// Starting loading app package list and returns a [Stream]
  /// that emits a new event every time a new package is loaded.
  ///
  /// Since some devices can have hundreds or even thousands of packages, this is great for lazy loading.
  ///
  /// - If [includeIcon] is true, it will return the [PackageInfo] with [icon] set to a non-null value representing the icon bitmap bytes.
  /// - If [includeSystemPackages] is true, it will return internal packages (if supported in the host platform).
  static Stream<PackageInfo> getInstalledPackagesAsStream({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) =>
      DevicePackagesPlatformInterface.instance.getInstalledPackagesAsStream(
        includeIcon: includeIcon,
        includeSystemPackages: includeSystemPackages,
      );

  static Future<PackageInfo> getPackage(
    String packageId, {
    bool includeIcon = kDefaultIncludeIcon,
  }) =>
      DevicePackagesPlatformInterface.instance
          .getPackage(packageId, includeIcon: includeIcon);

  static Future<bool> isPackageInstalled(String packageId) =>
      DevicePackagesPlatformInterface.instance.isPackageInstalled(packageId);

  static Stream<PackageEvent> listenToPackageEvents() =>
      DevicePackagesPlatformInterface.instance.listenToPackageEvents();

  static Future<void> openPackage(String packageId) =>
      DevicePackagesPlatformInterface.instance.openPackage(packageId);

  static Future<void> openPackageSettings(String packageId) =>
      DevicePackagesPlatformInterface.instance.openPackageSettings(packageId);

  static Future<void> installPackage({
    Uri? installerUri,
    String? installerPath,
    File? installerFile,
  }) =>
      DevicePackagesPlatformInterface.instance.installPackage(
        installerUri: installerUri,
        installerPath: installerPath,
        installerFile: installerFile,
      );

  static Future<void> uninstallPackage(String packageId) =>
      DevicePackagesPlatformInterface.instance.uninstallPackage(packageId);
}
