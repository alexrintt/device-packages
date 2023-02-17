import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'device_packages_method_channel.dart';

abstract class DevicePackagesPlatform extends PlatformInterface {
  /// Constructs a DevicePackagesPlatform.
  DevicePackagesPlatform() : super(token: _token);

  static final Object _token = Object();

  static DevicePackagesPlatform _instance = MethodChannelDevicePackages();

  /// The default instance of [DevicePackagesPlatform] to use.
  ///
  /// Defaults to [MethodChannelDevicePackages].
  static DevicePackagesPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DevicePackagesPlatform] when
  /// they register themselves.
  static set instance(DevicePackagesPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Stream<ApplicationPackage> didInstallPackage() {}
}

class ApplicationPackage {}
