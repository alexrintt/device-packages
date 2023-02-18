import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import './device_packages_platform_interface.dart';

/// An implementation of [DevicePackagesPlatformInterfacePlatform] that uses method channels.
class MethodChannelDevicePackagesPlatformInterface
    extends DevicePackagesPlatformInterface {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel(
    'io.alexrintt.device_packages.platform_interface.methodchannel/default',
  );

  /// The event channels used to interact with the native platform.
  @visibleForTesting
  final EventChannel installEventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.eventchannel/install',
  );
  @visibleForTesting
  final EventChannel uninstallEventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.eventchannel/uninstall',
  );
  @visibleForTesting
  final EventChannel getDevicePackagesEventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.eventchannel/getdevicepackages',
  );

  @override
  Stream<DevicePackage> getDevicePackagesAsStream({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
    };

    return getDevicePackagesEventChannel
        .receiveBroadcastStream(args)
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Future<List<DevicePackage>> getDevicePackages({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) async {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
    };

    final List<dynamic>? packages = await methodChannel
        .invokeMethod<List<dynamic>>('getDevicePackages', args);

    return Stream<dynamic>.fromIterable(packages!)
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer())
        .toList();
  }

  @override
  Future<int> getDevicePackageCount(
      {bool includeSystemPackages = false}) async {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeSystemPackages': '$includeSystemPackages',
    };

    final int? packageCount =
        await methodChannel.invokeMethod<int>('getDevicePackageCount', args);

    return packageCount!;
  }

  @override
  Stream<DevicePackage> didInstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
    };

    return installEventChannel
        .receiveBroadcastStream(args)
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<DevicePackage> didUninstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
    };

    return uninstallEventChannel
        .receiveBroadcastStream(args)
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
  static DevicePackage? _fromBroadcastStreamEvent(dynamic event) {
    if (event is! Map) return null;

    if (event.keys.any((dynamic key) => key is! String)) {
      return null;
    }

    final Map<String, dynamic> rawPackage = Map<String, dynamic>.from(event);

    return DevicePackage(
      icon: rawPackage['icon'] is List<int>
          ? Uint8List.fromList(rawPackage['icon'] as List<int>)
          : null,
      id: rawPackage['id'] as String?,
      name: rawPackage['name'] as String?,
      path: rawPackage['path'] as String?,
    );
  }

  static bool _devicePackageIsNotNull(DevicePackage? package) {
    return package != null && !package.isNull;
  }

  @override
  Stream<DevicePackage> bind(Stream<dynamic> stream) {
    return stream
        .map(_fromBroadcastStreamEvent)
        .where(_devicePackageIsNotNull)
        .cast<DevicePackage>();
  }

  @override
  StreamTransformer<RS, RT> cast<RS, RT>() {
    return StreamTransformer.castFrom<dynamic, DevicePackage, RS, RT>(this);
  }
}
