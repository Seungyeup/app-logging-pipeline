import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart'; // Import uuid package
import 'dart:convert';

import 'package:flutter_app/services/logging_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter API Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(title: 'Flutter API Demo'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _response = 'Press the button to call the API.';
  final Uuid _uuid = const Uuid(); // Initialize Uuid
  final LoggingService _loggingService = LoggingService();

  Future<void> _callApi_hello() async {
    setState(() {
      _response = 'Calling API...';
    });

    // Generate a UUID and remove hyphens to conform to the W3C Trace ID format (32-character hex string).
    final String globalId = _uuid.v4().replaceAll('-', ''); // Generate globalId

    try {
      print('Global ID: $globalId');
      // For Android emulators, use 10.0.2.2 to refer to the host machine's localhost.
      // For iOS simulators, you can use localhost or 127.0.0.1.
      // If you are running on a physical device, replace localhost with your computer's IP address.
      final response = await http.get(
        Uri.parse('http://127.0.0.1:8081/api/hello'),
        headers: {
          'X-Trace-Id': globalId, // Add traceId to header
        },
      );
      print('Response: ${response.body}');
      if (response.statusCode == 200) {
        setState(() {
          _response = 'Response: ${response.body}\nGlobal ID: $globalId';
        });
      } else {
        setState(() {
          _response = 'Error: ${response.statusCode}\nGlobal ID: $globalId';
        });
      }
    } catch (e) {
      setState(() {
        _response = 'Error: $e\nGlobal ID: $globalId';
      });
    }
  }

  Future<void> _callApi_hello_second() async {
    setState(() {
      _response = 'Calling API...';
    });
    // Generate a UUID and remove hyphens to conform to the W3C Trace ID format (32-character hex string).
    final String globalId = _uuid.v4().replaceAll('-', ''); // Generate globalId
    try {
      // For Android emulators, use 10.0.2.2 to refer to the host machine's localhost.
      // For iOS simulators, you can use localhost or 127.0.0.1.
      // If you are running on a physical device, replace localhost with your computer's IP address.
      final response = await http.get(
        Uri.parse('http://127.0.0.1:8081/api/hello2'),
        headers: {
          'X-Trace-Id': globalId, // Add traceId to header
        },
      );

      if (response.statusCode == 200) {
        setState(() {
          _response = 'Response: ${response.body}\nGlobal ID: $globalId';
        });
      } else {
        setState(() {
          _response = 'Error: ${response.statusCode}\nGlobal ID: $globalId';
        });
      }
    } catch (e) {
      setState(() {
        _response = 'Error: $e\nGlobal ID: $globalId';
      });
    }
  }

  Future<void> _sendSampleEvent() async {
    setState(() {
      _response = 'Sending event...';
    });

    final eventData = {
      'eventId': _uuid.v4(),
      'timestamp': DateTime.now().toIso8601String(),
      'userId': 'user-123',
      'event': 'button_click',
      'properties': {
        'button_id': 'send_event_button',
        'page': 'home',
      }
    };

    await _loggingService.sendEvent(eventData);

    setState(() {
      _response = 'Event sent! Check the console for details. \n' + eventData.toString();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              _response,
              textAlign: TextAlign.center, // Center align text
            ),
          ],
        ),
      ),
      floatingActionButton: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton(
            onPressed: _callApi_hello,
            tooltip: 'Call API hello',
            heroTag: 'hello',
            child: const Icon(Icons.api),
          ),
          const SizedBox(width: 16),
          FloatingActionButton(
            onPressed: _callApi_hello_second,
            tooltip: 'Call API hello second',
            heroTag: 'hello2',
            child: const Icon(Icons.api),
          ),
          const SizedBox(width: 16),
          FloatingActionButton(
            onPressed: _sendSampleEvent,
            tooltip: 'Send Log Event',
            heroTag: 'send_event',
            child: const Icon(Icons.event),
          ),
        ],
      ),
    );
  }
}