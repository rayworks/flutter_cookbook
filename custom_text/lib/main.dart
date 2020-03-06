import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.blue,
      ),
      home: MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const String loremIpsum =
      'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod '
      'tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim '
      'veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea '
      'commodo consequat. Duis aute irure dolor in reprehenderit in voluptate '
      'velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint '
      'occaecat cupidatat non proident, sunt in culpa qui officia deserunt '
      'mollit anim id est laborum.';

  @override
  Widget build(BuildContext context) {
    final mq = MediaQuery.of(context);
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            SizedBox(
              height: mq.size.height,
              width: 240.0,
              child: ListView(
                padding: EdgeInsets.all(4.0),
                children: <Widget>[
                  Container(
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.orange),
                    ),
                    child: Bubble(
                      text: TextSpan(
                        text: loremIpsum,
                        style: Theme.of(context).textTheme.body1,
                      ),
                    ),
                  ),
                  Container(
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.orange, width: 2.0),
                    ),
                    padding: EdgeInsets.symmetric(horizontal: 2.0),
                    child: Bubble(
                      text: TextSpan(
                        text: loremIpsum,
                        style: Theme.of(context).textTheme.body1,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class Bubble extends StatefulWidget {
  Bubble({@required this.text});

  final TextSpan text;

  @override
  _BubbleState createState() => new _BubbleState();
}

class _BubbleState extends State<Bubble> {
  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      // The text to render
      final textWidget = Text.rich(widget.text);

      // Calculate the left, top, bottom position of the end of the last text
      // line.
      final lastBox = _calcLastLineEnd(context, constraints);

      // Calculate whether the timestamp fits into the last line or if it has
      // to be positioned after the last line.
      final fitsLastLine =
          constraints.maxWidth - lastBox.right > Timestamp.size.width + 10.0;

      return Stack(
        children: [
          // Ensure the stack is big enough to render the text and the
          // timestamp.
          SizedBox.fromSize(
              size: Size(
                constraints.maxWidth,
                (fitsLastLine ? lastBox.top : lastBox.bottom) +
                    10.0 +
                    Timestamp.size.height,
              ),
              child: Container()),
          // Render the text.
          textWidget,
          // Render the timestamp.
          Positioned(
            left: constraints.maxWidth - (Timestamp.size.width + 10.0),
            top: (fitsLastLine ? lastBox.top : lastBox.bottom) + 5.0,
            child: Timestamp(DateTime.now()),
          ),
        ],
      );
    });
  }

  // Calculate the left, top, bottom position of the end of the last text
  // line.
  TextBox _calcLastLineEnd(BuildContext context, BoxConstraints constraints) {
    final richTextWidget = Text.rich(widget.text).build(context) as RichText;
    final renderObject = richTextWidget.createRenderObject(context);
    renderObject.layout(constraints);
    final lastBox = renderObject
        .getBoxesForSelection(TextSelection(
            baseOffset: 0, extentOffset: widget.text.toPlainText().length))
        .last;
    return lastBox;
  }
}

class Timestamp extends StatelessWidget {
  Timestamp(this.timestamp);

  final DateTime timestamp;

  /// This size could be calculated similarly to the way the text size in
  /// [Bubble] is calculated instead of using magic values.
  static final Size size = Size(60.0, 25.0);

  @override
  Widget build(BuildContext context) => Container(
        padding: EdgeInsets.all(3.0),
        decoration: BoxDecoration(
          color: Colors.greenAccent,
          border: Border.all(color: Colors.yellow),
        ),
        child:
            Text('${timestamp.hour}:${timestamp.minute}:${timestamp.second}'),
      );
}
