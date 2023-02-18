import 'dart:async';

import 'package:device_packages/device_packages.dart' show DevicePackage;
import 'package:device_packages/device_packages.dart' as packageManager;

import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  late Stream<DevicePackage> didInstallPackageStream;
  late Stream<DevicePackage> didUninstallPackageStream;

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    //didInstallPackageStream = packageManager.didInstallPackage();
    didUninstallPackageStream = packageManager.didUninstallPackage();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            StreamBuilder<DevicePackage>(
              stream: didUninstallPackageStream,
              builder: (
                BuildContext context,
                AsyncSnapshot<DevicePackage> snapshot,
              ) {
                if (!snapshot.hasData) {
                  return const Center(
                    child: Text('No packages were uninstalled...'),
                  );
                }

                return ListTile(
                  leading: snapshot.data?.icon != null
                      ? Image.memory(snapshot.data!.icon!)
                      : null,
                  title:
                      Text('Last uninstalled app was: ${snapshot.data?.name}'),
                  subtitle: Text('Package ID: ${snapshot.data?.id}'),
                );
              },
            ),
            const Divider(),
            // StreamBuilder<DevicePackage>(
            //   stream: didInstallPackageStream,
            //   builder: (
            //     BuildContext context,
            //     AsyncSnapshot<DevicePackage> snapshot,
            //   ) {
            //     if (!snapshot.hasData) {
            //       return const Center(
            //         child: Text('No packages were installed...'),
            //       );
            //     }
            //
            //     return ListTile(
            //       leading: snapshot.data?.icon != null
            //           ? Image.memory(snapshot.data!.icon!)
            //           : null,
            //       title:
            //           Text('Last uninstalled app was: ${snapshot.data?.name}'),
            //       subtitle: Text('Package ID: ${snapshot.data?.id}'),
            //     );
            //   },
            // ),
          ],
        ),
      ),
    );
  }
}
