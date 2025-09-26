
import 'dart:convert';
import 'package:http/http.dart' as http;

class LoggingService {
  // TODO: <INGEST-GATEWAY> 부분을 실제 Ingest Gateway의 주소로 변경해야 합니다.
  final String _ingestUrl = 'http://127.0.0.1:8081/ingest/events';

  Future<void> sendEvent(Map<String, dynamic> eventData) async {
    try {
      final response = await http.post(
        Uri.parse(_ingestUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
        body: jsonEncode(eventData),
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        print('Event sent successfully');
      } else {
        print('Failed to send event. Status code: ${response.statusCode}');
        print('Response body: ${response.body}');
      }
    } catch (e) {
      print('Error sending event: $e');
    }
  }
}
