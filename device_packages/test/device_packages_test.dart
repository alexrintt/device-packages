import 'package:flutter_test/flutter_test.dart';
import 'package:device_packages/device_packages.dart';
import 'package:device_packages/device_packages_platform_interface.dart';
import 'package:device_packages/device_packages_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockDevicePackagesPlatform
    with MockPlatformInterfaceMixin
    implements DevicePackagesPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final DevicePackagesPlatform initialPlatform = DevicePackagesPlatform.instance;

  test('$MethodChannelDevicePackages is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelDevicePackages>());
  });

  test('getPlatformVersion', () async {
    DevicePackages devicePackagesPlugin = DevicePackages();
    MockDevicePackagesPlatform fakePlatform = MockDevicePackagesPlatform();
    DevicePackagesPlatform.instance = fakePlatform;

    expect(await devicePackagesPlugin.getPlatformVersion(), '42');
  });
}
