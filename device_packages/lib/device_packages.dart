
import 'device_packages_platform_interface.dart';

class DevicePackages {
  Future<String?> getPlatformVersion() {
    return DevicePackagesPlatform.instance.getPlatformVersion();
  }
}
