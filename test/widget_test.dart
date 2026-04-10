import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:fortress_camera_controller/main.dart';

void main() {
  testWidgets('renders controller shell', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(2560, 1600);
    tester.view.devicePixelRatio = 2.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const FortressCameraControllerApp());

    expect(find.text('Fortress Camera Controller'), findsOneWidget);
    expect(find.text('Camera Viewer'), findsWidgets);
    expect(find.text('Open sections'), findsOneWidget);

    await tester.tap(find.byTooltip('Open controls'));
    await tester.pumpAndSettle();

    expect(find.text('Camera Controls'), findsOneWidget);

    await tester.tap(find.widgetWithText(ListTile, 'Video Settings'));
    await tester.pumpAndSettle();

    expect(find.text('Video Controls'), findsOneWidget);
    expect(find.text('Frame Width'), findsOneWidget);
    expect(find.text('Frame Height'), findsOneWidget);
    expect(find.text('Get'), findsOneWidget);
    expect(find.text('Set'), findsOneWidget);
  });
}
