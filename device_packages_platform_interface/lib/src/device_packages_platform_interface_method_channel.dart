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

  int _generateId() => UniqueKey().hashCode;

  @override
  Stream<DevicePackage> getDevicePackagesAsStream({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'eventName': 'getDevicePackagesAsStream',
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
      'listenerId': '${_generateId()}',
    };

    return eventChannel
        .receiveBroadcastStream(args)
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<DevicePackage> didInstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'eventName': 'didInstallPackage',
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
      'listenerId': '${_generateId()}',
    };

    return eventChannel
        .receiveBroadcastStream(args)
        .transform(_RawBroadcastEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<DevicePackage> didUninstallPackage({
    bool includeIcon = false,
    bool includeSystemPackages = false,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'eventName': 'didUninstallPackage',
      'includeIcon': '$includeIcon',
      'includeSystemPackages': '$includeSystemPackages',
      'listenerId': '${_generateId()}',
    };

    return eventChannel
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
