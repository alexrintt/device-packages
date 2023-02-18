import 'package:device_packages_platform_interface/device_packages_platform_interface.dart';

export 'package:device_packages_platform_interface/device_packages_platform_interface.dart'
    show DevicePackage;

export 'package:device_packages_platform_interface/device_packages_platform_interface.dart'
    show DevicePackagesPlatformInterface;

/// Returns all packages from the device, note that this may cause several
/// performance issues if called on the main UI thread.
///
/// Since this is a [Future], this cannot be used in a lazy load. You do not have any
/// feedback until all packages are loaded; again, this can take several seconds
/// on devices with more than 100+ packages (which is common on real Android devices).
///
/// It is recommended to use [getDevicePackagesAsStream] instead.
///
/// - If [includeIcon] is true, it will return the [DevicePackage] with [icon] set to a non-null value representing the icon bitmap bytes.
/// - If [includeSystemPackages] is true, it will return internal packages (if supported in the host platform).
Future<List<DevicePackage>> getDevicePackages({
  bool includeIcon = true,
  bool includeSystemPackages = true,
}) =>
    DevicePackagesPlatformInterface.instance.getDevicePackages(
      includeIcon: includeIcon,
      includeSystemPackages: includeSystemPackages,
    );

/// Returns a new [Stream] that emits a new event whenever the device uninstall a package.
///
/// This does not emit an event when this app itself is uninstalled.
///
/// - On Android in the default implementation, it relies to the `ACTION_PACKAGE_REMOVED` intent to receive broadcast events from the Android OS.
Stream<DevicePackage> didUninstallPackage({
  bool includeIcon = false,
  bool includeSystemPackages = false,
}) =>
    DevicePackagesPlatformInterface.instance.didUninstallPackage(
      includeIcon: includeIcon,
      includeSystemPackages: includeSystemPackages,
    );

/// Returns a new [Stream] that emits a new event whenever the device install a package.
///
/// This does not emit an event when this app itself is uninstalled.
///
/// - On Android in the default implementation, it relies to the `ACTION_PACKAGE_ADDED` intent to receive broadcast events from the Android OS.
Stream<DevicePackage> didInstallPackage({
  bool includeIcon = false,
  bool includeSystemPackages = false,
}) =>
    DevicePackagesPlatformInterface.instance.didInstallPackage(
      includeIcon: includeIcon,
      includeSystemPackages: includeSystemPackages,
    );

/// Returns a future that completes with the device total package count.
///
/// Useful to show progress of a loading indicator.
Future<int> getDevicePackageCount({
  bool includeIcon = false,
  bool includeSystemPackages = false,
}) =>
    DevicePackagesPlatformInterface.instance
        .getDevicePackageCount(includeSystemPackages: includeSystemPackages);

/// Starting loading app package list and returns a [Stream]
/// that emits a new event every time a new package is loaded.
///
/// Since some devices can have hundreds or even thousands of packages, this is great for lazy loading.
///
/// - If [includeIcon] is true, it will return the [DevicePackage] with [icon] set to a non-null value representing the icon bitmap bytes.
/// - If [includeSystemPackages] is true, it will return internal packages (if supported in the host platform).
Stream<DevicePackage> getDevicePackagesAsStream({
  bool includeIcon = false,
  bool includeSystemPackages = false,
}) =>
    DevicePackagesPlatformInterface.instance.getDevicePackagesAsStream(
      includeIcon: includeIcon,
      includeSystemPackages: includeSystemPackages,
    );
