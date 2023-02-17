import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'device_packages_platform_interface_method_channel.dart';

abstract class DevicePackagesPlatformInterfacePlatform
    extends PlatformInterface {
  /// Constructs a DevicePackagesPlatformInterfacePlatform.
  DevicePackagesPlatformInterfacePlatform() : super(token: _token);

  static final Object _token = Object();

  static DevicePackagesPlatformInterfacePlatform _instance =
      MethodChannelDevicePackagesPlatformInterface();

  /// The default instance of [DevicePackagesPlatformInterfacePlatform] to use.
  ///
  /// Defaults to [MethodChannelDevicePackagesPlatformInterface].
  static DevicePackagesPlatformInterfacePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DevicePackagesPlatformInterfacePlatform] when
  /// they register themselves.
  static set instance(DevicePackagesPlatformInterfacePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion();

  Stream<DevicePackage> getDevicePackagesAsStream({
    bool includeIcon = false,
  });

  Future<List<DevicePackage>> getDevicePackages({
    bool includeIcon = false,
  }) async {
    return getDevicePackagesAsStream(includeIcon: includeIcon).toList();
  }

  Stream<DevicePackage> didUninstallPackage({
    bool includeIcon = false,
  });

  Stream<DevicePackage> didInstallPackage({
    bool includeIcon = false,
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
