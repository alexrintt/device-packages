import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'device_packages_platform_interface.dart';

/// An implementation of [DevicePackagesPlatformInterface] that uses method channels.
class DevicePackagesPlatformInterfaceMethodChannel
    extends DevicePackagesPlatformInterface {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel(
    'io.alexrintt.device_packages.platform_interface.methodchannel/default',
  );

  /// The event channels used to interact with the native platform.
  @visibleForTesting
  final EventChannel packagesEventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.eventchannel/packages',
  );
  @visibleForTesting
  final EventChannel getInstalledPackagesEventChannel = const EventChannel(
    'io.alexrintt.device_packages.platform_interface.eventchannel/getinstalledpackages',
  );

  @override
  Future<int> getInstalledPackageCount({
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  }) async {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeSystemPackages': includeSystemPackages,
      'onlyOpenablePackages': includeSystemPackages,
    };

    return methodChannel
        .invokeMethod<int>('getInstalledPackageCount', args)
        .cast<int>();
  }

  @override
  Future<List<PackageInfo>> getInstalledPackages({
    bool includeIcon = kDefaultIncludeIcon,
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  }) async {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': includeIcon,
      'includeSystemPackages': includeSystemPackages,
      'onlyOpenablePackages': onlyOpenablePackages,
    };

    return methodChannel
        .invokeMethod<List<dynamic>>('getInstalledPackages', args)
        .cast<List<dynamic>>()
        .apply(_RawEventToDevicePackageStreamTransformer());
  }

  @override
  Stream<PackageInfo> getInstalledPackagesAsStream({
    bool includeIcon = kDefaultIncludeIcon,
    bool includeSystemPackages = kDefaultIncludeSystemPackages,
    bool onlyOpenablePackages = kDefaultOnlyOpenablePackages,
  }) {
    final Map<String, dynamic> args = <String, dynamic>{
      'includeIcon': includeIcon,
      'includeSystemPackages': includeSystemPackages,
      'onlyOpenablePackages': onlyOpenablePackages,
    };

    return getInstalledPackagesEventChannel
        .receiveBroadcastStream(args)
        .transform<PackageInfo>(_RawEventToDevicePackageStreamTransformer());
  }

  @override
  Future<PackageInfo> getPackage(
    String packageId, {
    bool includeIcon = kDefaultIncludeIcon,
  }) {
    try {
      final Map<String, dynamic> args = <String, dynamic>{
        'packageId': packageId,
        'includeIcon': includeIcon,
      };

      return methodChannel
          .invokeMethod<dynamic>('getPackage', args)
          .transform<PackageInfo>(_RawEventToDevicePackageStreamTransformer());
    } on PlatformException catch (e) {
      switch (e.code) {
        case PackageNotFoundException.code:
          throw PackageNotFoundException('${e.message}\nDetails:${e.details}');
        default:
          rethrow;
      }
    }
  }

  @override
  Future<void> installPackage({
    Uri? installerUri,
    String? installerPath,
    File? installerFile,
  }) async {
    assert(
      installerUri != null || installerPath != null || installerFile != null,
      '''You must define at least one installer source, [installerUri], [installerPath] or [installerFile].''',
    );

    final Map<String, dynamic> args = <String, dynamic>{
      if (installerUri != null) 'installerUri': '$installerUri',
      if (installerPath != null || installerFile != null)
        'installerPath': installerPath ?? installerFile!.path,
    };

    try {
      return await methodChannel.invokeMethod<void>('installPackage', args);
    } on PlatformException catch (e) {
      switch (e.code) {
        case MissingPermissionToRequestInstallPackageError.code:
          throw MissingPermissionToRequestInstallPackageError(e.message);
        case InvalidInstallerException.code:
          throw InvalidInstallerException('${e.message}\nDetails:${e.details}');
        case UnsuccessfulPackageInstallRequestException.code:
          throw UnsuccessfulPackageInstallRequestException(
            '${e.message}\nDetails:${e.details}',
          );
        default:
          rethrow;
      }
    }
  }

  @override
  Future<bool> isPackageInstalled(String packageId) {
    final Map<String, dynamic> args = <String, dynamic>{'packageId': packageId};

    return methodChannel
        .invokeMethod<bool>('isPackageInstalled', args)
        .cast<bool>();
  }

  @override
  Stream<PackageEvent> listenToPackageEvents() {
    return packagesEventChannel
        .receiveBroadcastStream()
        .transform(_RawEventToPackageEventStreamTransformer());
  }

  @override
  Future<void> openPackage(String packageId) async {
    final Map<String, dynamic> args = <String, dynamic>{'packageId': packageId};

    try {
      await methodChannel.invokeMethod<void>('openPackage', args);
    } on PlatformException catch (e) {
      switch (e.code) {
        case PackageIsNotOpenableException.code:
          throw const PackageIsNotOpenableException(
            'This is not an openable package, on Android this means this package has no launch intent',
          );
        default:
          rethrow;
      }
    }
  }

  @override
  Future<void> openPackageSettings(String packageId) {
    final Map<String, dynamic> args = <String, dynamic>{'packageId': packageId};

    return methodChannel.invokeMethod<void>('openPackageSettings', args);
  }

  @override
  Future<void> uninstallPackage(String packageId) {
    final Map<String, dynamic> args = <String, dynamic>{'packageId': packageId};

    return methodChannel.invokeMethod<void>('uninstallPackage', args);
  }
}

/// On Android, this exception is thrown when [installPackage] is called with an
/// uri or path that is not a valid apk.
class InvalidInstallerException extends _DevicePackagesException {
  const InvalidInstallerException([super.message]);

  static const String code = 'InvalidInstallerException';
}

/// On Android, this exception is thrown when [openPackage] is called on a package that has no launch intent.
class PackageIsNotOpenableException extends _DevicePackagesException {
  const PackageIsNotOpenableException([super.message]);

  static const String code = 'PackageIsNotOpenableException';
}

/// On Android, this exception is thrown when the app has no permission over the
/// apk installer file, so this can not launch a new activity.
class UnsuccessfulPackageInstallRequestException
    extends _DevicePackagesException {
  const UnsuccessfulPackageInstallRequestException([super.message]);

  static const String code = 'UnsuccessfulPackageInstallRequestException';
}

/// On Android, it is thrown when [installPackage] is called and your app does not declare [REQUEST_INSTALL_PACKAGES] permission.
class MissingPermissionToRequestInstallPackageError
    extends _DevicePackagesError {
  MissingPermissionToRequestInstallPackageError([super.message]);

  static const String code = 'MissingPermissionToRequestInstallPackageError';
}

/// On Android, it is thrown when [getPackage] is called with an [packageId]
/// that the application has no permission over or the application simply is not installed.
class PackageNotFoundException extends _DevicePackagesException {
  PackageNotFoundException([super.message]);

  static const String code = 'PackageNotFoundException';
}

/// A generic [StreamTransformer] implementation that convert all events using
/// [mapStreamEvent] abstract method and that skips all null events.
abstract class _NonNullableEventStreamTransformer<S, T>
    implements StreamTransformer<S, T> {
  const _NonNullableEventStreamTransformer();

  T? mapStreamEvent(S event);

  bool _eventIsNotNull(T? package) {
    return package != null;
  }

  @override
  Stream<T> bind(Stream<S> stream) {
    return stream.map(mapStreamEvent).where(_eventIsNotNull).cast<T>();
  }

  @override
  StreamTransformer<RS, RT> cast<RS, RT>() {
    return StreamTransformer.castFrom<S, T, RS, RT>(this);
  }
}

/// The [_RawEventToDevicePackageStreamTransformer]
/// transformer class is responsible for the followig tasks:
///
/// 1. Ignore (filter) null packages since they are not useful.
/// 2. Cast to a typed map.
/// 3. Convert every typed map to a [PackageInfo].
///
/// Note, this transformer will discard any event that is not a map (including null).
class _RawEventToDevicePackageStreamTransformer
    extends _NonNullableEventStreamTransformer<dynamic, PackageInfo> {
  @override
  PackageInfo? mapStreamEvent(dynamic event) {
    if (event is! Map) return null;

    if (event.keys.any((dynamic key) => key is! String)) {
      return null;
    }

    final Map<String, dynamic> rawPackage = Map<String, dynamic>.from(event);

    return PackageInfo(
      icon: rawPackage['icon'] is List<int>
          ? Uint8List.fromList(rawPackage['icon'] as List<int>)
          : null,
      id: rawPackage['id'] as String?,
      name: rawPackage['name'] as String?,
      installerPath: rawPackage['installerPath'] as String?,
      isSystemPackage: rawPackage['isSystemPackage'] as bool?,
      isOpenable: rawPackage['isOpenable'] as bool?,
      length: rawPackage['length'] as int?,
      versionName: rawPackage['versionName'] as String?,
    );
  }
}

/// The [_RawEventToDevicePackageStreamTransformer]
/// transformer class is responsible for the followig tasks:
///
/// 1. Ignore (filter) null packages since they are not useful.
/// 2. Cast to a typed map.
/// 3. Convert every typed map to a [PackageEvent].
///
/// Note, this transformer will discard any event that is not a map (including null).
class _RawEventToPackageEventStreamTransformer
    extends _NonNullableEventStreamTransformer<dynamic, PackageEvent> {
  @override
  PackageEvent? mapStreamEvent(dynamic event) {
    if (event is! Map) return null;

    if (event.keys.any((dynamic key) => key is! String)) {
      return null;
    }

    final Map<String, dynamic> rawPackageEvent =
        Map<String, dynamic>.from(event);

    if (rawPackageEvent['action'] is! String) return null;
    if (rawPackageEvent['packageId'] is! String) return null;

    final PackageAction? action =
        PackageAction.parseFromString(rawPackageEvent['action'] as String);

    if (action == null) return null;

    final String packageId = rawPackageEvent['packageId'] as String;

    return PackageEvent(
      action: action,
      packageId: packageId,
    );
  }
}

extension<T> on Future<T> {
  Future<S> cast<S>() async => await this as S;

  Future<S> transform<S>(StreamTransformer<T, S> transformer) =>
      Stream<T>.fromFuture(this).transform<S>(transformer).first;
}

extension<T> on Future<List<T>> {
  Future<List<S>> apply<S>(StreamTransformer<T, S> transformer) async =>
      Stream<T>.fromIterable(await this).transform<S>(transformer).toList();
}

class _DevicePackagesException implements Exception {
  const _DevicePackagesException([this.message]);

  final String? message;

  @override
  String toString() => 'Message: $message';
}

class _DevicePackagesError extends Error {
  _DevicePackagesError([this.message]);

  final String? message;

  @override
  String toString() => 'Message: $message';
}
