import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

enum AttendanceStatus {
  success, // 200
  expiredToken, // 401
  antiProxyConflict, // 409
  networkError, // Timeout / socket / unreachable
  invalidPayload, // Malformed QR code JSON
  unknownError,
}

class AttendanceResponse {
  final AttendanceStatus status;
  final int? statusCode;
  final String message;
  final String? studentName;
  final String? rawResponse;

  const AttendanceResponse({
    required this.status,
    this.statusCode,
    required this.message,
    this.studentName,
    this.rawResponse,
  });
}

class ApiService {
  static const Duration requestTimeout = Duration(seconds: 4);

  /// Dispatches the attendance HTTP POST request to the local server endpoint over Wi-Fi intranet.
  static Future<AttendanceResponse> submitAttendance({
    required String serverUrl,
    required String token,
    required String studentId,
    required String nationalId,
    required String fullName,
    required String deviceUuid,
  }) async {
    final Uri? parsedUri = Uri.tryParse(serverUrl);
    if (parsedUri == null || !parsedUri.hasScheme) {
      return const AttendanceResponse(
        status: AttendanceStatus.invalidPayload,
        message: 'Invalid server URL in QR payload.',
      );
    }

    final Map<String, dynamic> requestBody = {
      'token': token,
      'student_id': studentId,
      'national_id': nationalId,
      'full_name': fullName,
      'device_uuid': deviceUuid,
    };

    try {
      final response = await http
          .post(
            parsedUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(requestBody),
          )
          .timeout(requestTimeout);

      if (response.statusCode == 200) {
        return AttendanceResponse(
          status: AttendanceStatus.success,
          statusCode: 200,
          studentName: fullName,
          message: 'Attendance recorded successfully.',
          rawResponse: response.body,
        );
      } else if (response.statusCode == 409) {
        return AttendanceResponse(
          status: AttendanceStatus.antiProxyConflict,
          statusCode: 409,
          message:
              'Anti-Proxy Alert: This device has already logged attendance for another student!',
          rawResponse: response.body,
        );
      } else if (response.statusCode == 401) {
        return AttendanceResponse(
          status: AttendanceStatus.expiredToken,
          statusCode: 401,
          message: 'Expired QR token. Please scan the current code on screen.',
          rawResponse: response.body,
        );
      } else {
        return AttendanceResponse(
          status: AttendanceStatus.unknownError,
          statusCode: response.statusCode,
          message:
              'Server returned error ${response.statusCode}: ${response.reasonPhrase ?? "Request failed"}',
          rawResponse: response.body,
        );
      }
    } on TimeoutException {
      return const AttendanceResponse(
        status: AttendanceStatus.networkError,
        message:
            'Cannot reach server. Ensure you are connected to the lecture Wi-Fi.',
      );
    } on SocketException {
      return const AttendanceResponse(
        status: AttendanceStatus.networkError,
        message:
            'Cannot reach server. Ensure you are connected to the lecture Wi-Fi.',
      );
    } on http.ClientException {
      return const AttendanceResponse(
        status: AttendanceStatus.networkError,
        message:
            'Cannot reach server. Ensure you are connected to the lecture Wi-Fi.',
      );
    } catch (e) {
      return AttendanceResponse(
        status: AttendanceStatus.networkError,
        message:
            'Cannot reach server. Ensure you are connected to the lecture Wi-Fi.',
        rawResponse: e.toString(),
      );
    }
  }
}
