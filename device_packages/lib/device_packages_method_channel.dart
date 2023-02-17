import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'device_packages_platform_interface.dart';

/// An implementation of [DevicePackagesPlatform] that uses method channels.
class MethodChannelDevicePackages extends DevicePackagesPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('device_packages');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
