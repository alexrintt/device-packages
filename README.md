> **Warning** this is a WIP.

# Device Packages plugin for Flutter

![Plugin badge version](https://img.shields.io/pub/v/device_packages.svg?style=for-the-badge&color=22272E&showLabel=false&labelColor=15191f&logo=dart&logoColor=blue)

A plugin to list installed applications on an Android device (iOS is not supported). You can also listen to app changes (install, uninstall and update).

## Getting Started

Check the latest version on [pub.dev](https://pub.dev/packages/device_packages).

```yaml
dependencies:
  device_packages: <latest-version>
```

Import:

```dart
import 'package:device_packages/device_packages.dart';
```

## Android permissions

Since Android 11 (API level 30), most user-installed apps are not visible by default. In your manifest, you must statically declare which apps you are going to get info about, as in the following:

```xml
<manifest>
  <queries>
    <!-- Explicit apps you know in advance about: -->
    <package android:name="com.example.this.app"/>
    <package android:name="com.example.this.other.app"/>
  </queries>
  ...
</manifest>
```

- Reference https://stackoverflow.com/questions/18752202/check-if-application-is-installed-android.

---

You also must include these permissions:

```xml
<!-- To request install an apk -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- To request uninstall a package -->
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />

<!-- To get all installed apps -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

## List installed packages

To list installed packages on the device:

```dart
// Recommended if you are displaying the apps in list view.
// You can use the stream to lazy-load and improve the UI/UX.
Stream<PackageInfo> packagesStream = await DevicePackages.getInstalledPackagesAsStream();

// Recommended if you are performing a background task 
// and don't mind taking several seconds to load all pakckages.
List<PackageInfo> packages = await DevicePackages.getInstalledPackages();
```

You can filter system apps if necessary.

**Note**: The list of apps is not ordered! You have to do it yourself.

### Get openable packages

On android, an openable package means you can launch the application.

To list only the apps with launch intents, simply use the `onlyOpenablePackages: true` attribute.

```dart
// Returns a list of only those apps that have launch intent (for Android)
// This is also available for [getInstalledPackages].
Stream<PackageInfo> packagesStream = DevicePackages.getInstalledPackagesAsStream(
  onlyOpenablePackages: true, 
  includeSystemApps: true
)
```

## Get a package

To get a specific application info, provide its package id:

```dart
PackageInfo package = await DevicePackages.getPackage('io.alexrintt.kanade');
```

## Check if an application is installed

To check if an app is installed (via its package id):

```dart
bool isInstalled = await DevicePackages.isPackageInstalled('io.alexrintt.kanade');
```

## Open a package

To open a package (on Android the package must have a launch Intent).

```dart
DevicePackages.openPackage('io.alexrintt.kanade');
```

## Open a package settings screen

To open package settings:

```dart
DevicePackages.openPackageSettings('io.alexrintt.kanade');
```

## Uninstall an package

To open the screen to uninstall an package:

1. Add this permission to the `AndroidManifest.xml` file:

```xml
<!-- Remember to add the permission -->
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
```

2. Call this method:

```dart
DevicePackages.uninstallPackage('io.alexrintt.kanade');
```

## Include package icon

When calling `getInstalledPackages()` or `getPackage()` methods, you can also ask for the icon.
To display the image, just call:

```dart
Image.memory(app.icon);
```

## Listen to package events

To listen to package events on the device (installation, uninstallation, update):

```dart
Stream<ApplicationEvent> apps = await DevicePackages.listenToAppsChanges();
```

If you only need events for a single app, just use the `Stream` API, like so:

```dart
DevicePackages.listenToPackageEvents().where(
  (PackageEvent event) => event.packageId == 'io.alexrintt.kanade'
)
```

To get package details from the event, use the `DevicePackages.getPackage` API:

```dart
DevicePackages.listenToPackageEvents().liste(
  (PackageEvent event) async {
    if (!await DevicePackages.isPackageInstalled(event.packageId)) {
      // You do not have access to package info since
      // the package was uninstalled.
      // Although you can retrive from your cache (if any).
    } else {
      final PackageInfo package = 
          await DevicePackages.getPackage(event.packageId);

      print('Action: ${event.action}');
      print('Package name: ${package.name}');
      print('Package id: ${package.id}');
      print('Is system package: ${package.isSystemPackage}');

      // Apk file path on Android.
      print('Package installerPath: ${package.installerPath}');
    }
  },
)
```


## Footnotes

This plugin was initially a fork of `device_apps` https://github.com/g123k/flutter_plugin_device_apps, but since the plugin author is no longer maintaining the project (resolving issues or merging pull requests) I did decide to create this plugin, make some improvements and add some features:

- [x] Java to Kotlin fully migration.
- [x] List installed apps in lazy-load mode https://github.com/g123k/flutter_plugin_device_apps/pull/90.
- [x] Install packages, on Android this means being able to start a new activity to install an apk file/uri.
- [x] Fixed unresolved bug on `listenToPackageEvents` https://github.com/g123k/flutter_plugin_device_apps/issues/97.
- [ ] Support for getting the current Android launcher package https://github.com/g123k/flutter_plugin_device_apps/pull/82.
- [ ] Getting package info from intaller file/uri, on Android this means generating the `PackageInfo` from an apk file https://github.com/g123k/flutter_plugin_device_apps/pull/92.
- [ ] Sign-in apk files https://stackoverflow.com/questions/10630796/signing-apk-files-programmatically-in-java.

