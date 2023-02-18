import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'device_packages_platform_interface_method_channel.dart';

abstract class DevicePackagesPlatformInterface extends PlatformInterface {
  /// Constructs a DevicePackagesPlatformInterface.
  DevicePackagesPlatformInterface() : super(token: _token);

  static final Object _token = Object();

  static DevicePackagesPlatformInterface _instance =
      MethodChannelDevicePackagesPlatformInterface();

  /// The default instance of [DevicePackagesPlatformInterface] to use.
  ///
  /// Defaults to [MethodChannelDevicePackagesPlatformInterfaceInterface].
  static DevicePackagesPlatformInterface get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DevicePackagesPlatformInterface] when
  /// they register themselves.
  static set instance(DevicePackagesPlatformInterface instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<List<DevicePackage>> getDevicePackages({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  });

  Stream<DevicePackage> getDevicePackagesAsStream({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  });

  Future<int> getDevicePackageCount({
    bool includeSystemPackages = false,
  });

  Stream<DevicePackage> didUninstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  });

  Stream<DevicePackage> didInstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  });
}

class DevicePackage {
  const DevicePackage({this.id, this.name, this.icon, this.path});

  final String? id;
  final String? name;
  final Uint8List? icon;
  final String? path;

  bool get isNull => id == null && name == null && icon == null && path == null;
}
