import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import './device_packages_platform_interface.dart';

/// An implementation of [DevicePackagesPlatformInterfacePlatform] that uses method channels.
class MethodChannelDevicePackagesPlatformInterface
    extends DevicePackagesPlatformInterfacePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel(
    'io.alexrintt.device_packages.platform_interface.default/methodchannel',
  );

  /// The event channel used to interact with the native platform.
  @visibleForTesting
  final EventChannel eventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.default/eventchannel',
  );

  @override
  Stream<DevicePackage> getDevicePackagesAsStream({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    return eventChannel
        .receiveBroadcastStream('getDevicePackagesAsStream')
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<DevicePackage> didInstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    return eventChannel
        .receiveBroadcastStream('didInstallPackage')
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<DevicePackage> didUninstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    return eventChannel
        .receiveBroadcastStream('didUninstallPackage')
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }
}

/// This [StreamTransformer] will:
///
/// 1. Ignore (filter) null packages since they are not useful.
/// 2. Cast to a typed map.
/// 3. Convert every typed map to a [DevicePackage].
///
/// Note, this transformer will discard any event that is not a map (including null).
/// This also will discard any [DevicePackage] instance that [isNull], since they are also not useful.
class _RawBroadcastEventToDevicePackageStreamTransformer
    implements StreamTransformer<dynamic, DevicePackage> {
  static bool _filterBroadcastStreamEvent(dynamic event) {
    return event is Map<String, dynamic>;
  }

  static DevicePackage _fromBroadcastStreamEvent(Map<String, dynamic> event) {
    return DevicePackage(
      icon: event['icon'] is List<int>
          ? Uint8List.fromList(event['icon'] as List<int>)
          : null,
      id: event['id'] as String?,
      name: event['name'] as String?,
      path: event['path'] as String?,
    );
  }

  static bool _devicePackageIsNotNull(DevicePackage package) {
    return !package.isNull;
  }

  @override
  Stream<DevicePackage> bind(Stream<dynamic> stream) {
    return stream
        .where(_filterBroadcastStreamEvent)
        .cast<Map<String, dynamic>>()
        .map(_fromBroadcastStreamEvent)
        .where(_devicePackageIsNotNull);
  }

  @override
  StreamTransformer<RS, RT> cast<RS, RT>() {
    return StreamTransformer.castFrom<dynamic, DevicePackage, RS, RT>(this);
  }
}
