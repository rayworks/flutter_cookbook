import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:flutter/services.dart';
import 'dart:async';

import 'navigation_controls.dart';

void main () {
  runApp(MaterialApp(
    title: 'Something',
    home: AplicativoB2b(),
    debugShowCheckedModeBanner: false,
  ));
  SystemChrome.setEnabledSystemUIOverlays ([]);
}

class AplicativoB2b extends StatefulWidget {
  @override
  _AplicativoB2bState createState() => _AplicativoB2bState();
}

class _AplicativoB2bState extends State<AplicativoB2b> {

  Completer<WebViewController> _controller = Completer<WebViewController>();

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      body: WebView(
        initialUrl: 'https://bing.com',
        javascriptMode: JavascriptMode.unrestricted,
        onWebViewCreated: (WebViewController webViewController) {
           _controller.complete(webViewController);
        },
      ),
      floatingActionButton: NavigationControls(_controller.future), // <-- added this
      );
  }
}