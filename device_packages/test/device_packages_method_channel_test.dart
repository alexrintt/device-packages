import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:device_packages/device_packages_method_channel.dart';

void main() {
  MethodChannelDevicePackages platform = MethodChannelDevicePackages();
  const MethodChannel channel = MethodChannel('device_packages');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
