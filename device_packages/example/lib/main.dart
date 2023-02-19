import 'dart:async';

import 'package:device_packages/device_packages.dart';

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
    _initializePackageEventsStream();
  }

  late Stream<Map<PackageAction, PackageInfo>> _packageEventsStream;

  Future<void> _initializePackageEventsStream() async {
    _packageEventsStream = DevicePackages.listenToPackageEvents()
        .asyncMap<Map<PackageAction, PackageInfo>>(
      (PackageEvent event) async {
        if (await DevicePackages.isPackageInstalled(event.packageId)) {
          return <PackageAction, PackageInfo>{
            event.action: await DevicePackages.getPackage(event.packageId),
          };
        }
        return <PackageAction, PackageInfo>{
          event.action: PackageInfo(id: event.packageId),
        };
      },
    );
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
            StreamBuilder<Map<PackageAction, PackageInfo>>(
              stream: _packageEventsStream,
              builder: (
                BuildContext context,
                AsyncSnapshot<Map<PackageAction, PackageInfo>> snapshot,
              ) {
                if (!snapshot.hasData) {
                  return const Center(
                    child: Text('No events yet...'),
                  );
                }

                final PackageAction action = snapshot.data!.keys.first;
                final PackageInfo devicePackage = snapshot.data!.values.first;

                return ListTile(
                  leading: devicePackage.icon != null
                      ? Image.memory(devicePackage.icon!)
                      : null,
                  title: Text('App name: ${devicePackage.name}'),
                  subtitle: Text(
                    'Last event: ${action.name} | Package ID: ${devicePackage.id}',
                  ),
                );
              },
            ),
            const Divider(),
          ],
        ),
      ),
    );
  }
}
