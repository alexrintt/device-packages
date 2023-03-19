import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'device_packages_platform_interface_method_channel.dart';

const bool kDefaultIncludeSystemPackages = false;
const bool kDefaultIncludeIcon = false;
const bool kDefaultOnlyOpenablePackages = false;

abstract class DevicePackagesPlatformInterface extends PlatformInterface {
  /// Constructs a DevicePackagesPlatformInterface.
  DevicePackagesPlatformInterface() : super(token: _token);

  static final Object _token = Object();

  static DevicePackagesPlatformInterface _instance =
      DevicePackagesPlatformInterfaceMethodChannel();

  /// The default instance of [DevicePackagesPlatformInterface] to use.
  ///
  /// Defaults to [DevicePackagesPlatformInterfaceMethodChannel].
  static DevicePackagesPlatformInterface get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DevicePackagesPlatformInterface] when
  /// they register themselves.
  static set instance(DevicePackagesPlatformInterface instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<List<PackageInfo>> getInstalledPackages({
    bool includeIcon = kDefaultIncludeIcon,
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  });

  Stream<PackageInfo> getInstalledPackagesAsStream({
    bool includeIcon = kDefaultIncludeIcon,
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  });

  Future<int> getInstalledPackageCount({
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  });

  Future<bool> isPackageInstalled(String packageId);

  Future<PackageInfo> getPackage(
    String packageId, {
    bool includeIcon = kDefaultIncludeIcon,
  });

  Stream<PackageEvent> listenToPackageEvents();

  Future<void> openPackage(String packageId);

  Future<void> openPackageSettings(String packageId);

  Future<void> uninstallPackage(String packageId);

  Future<void> installPackage({
    Uri? installerUri,
    String? installerPath,
    File? installerFile,
  });
}

String getFileSizeString(int bytes, {int decimals = 0}) {
  const List<String> suffixes = <String>['B', 'KB', 'MB', 'GB', 'TB'];

  if (bytes == 0) return '0${suffixes[0]}';

  final int i = (log(bytes) / log(1024)).floor();

  return ((bytes / pow(1024, i)).toStringAsFixed(decimals)) + suffixes[i];
}

extension FormattedBytes on num {
  String formatBytes() => getFileSizeString(this ~/ 1);
}

class PackageInfo {
  const PackageInfo({
    this.id,
    this.name,
    this.icon,
    this.installerPath,
    this.isSystemPackage,
    this.isOpenable,
    this.length,
  });

  String get nameWithFormattedSize => '$name $formattedSize';

  String get formattedSize => length != null ? length!.formatBytes() : '';

  final int? length;

  /// The idenfier of this package within the OS.
  final String? id;

  /// The package display name, it is a human readable label.
  final String? name;

  /// The package icon bitmap bytes.
  final Uint8List? icon;

  /// On Android this is the apk path.
  final String? installerPath;

  /// Whether or not this package was built onto the system image.
  final bool? isSystemPackage;

  /// Whether or not this package can be opened.
  final bool? isOpenable;
}

class PackageEvent {
  const PackageEvent({required this.packageId, required this.action});

  /// The event performed action.
  final PackageAction action;

  /// The [packageId] you can use with [packageManager.getPackageInfo].
  final String packageId;
}

enum PackageAction {
  install,
  update,
  uninstall;

  bool get installed => this == PackageAction.install;
  bool get uninstalled => this == PackageAction.uninstall;
  bool get updated => this == PackageAction.update;

  static PackageAction? parseFromString(String name) {
    switch (name) {
      case 'install':
        return PackageAction.install;
      case 'uninstall':
        return PackageAction.uninstall;
      case 'update':
        return PackageAction.update;
      default:
        return null;
    }
  }
}
