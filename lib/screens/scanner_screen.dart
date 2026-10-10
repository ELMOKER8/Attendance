import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/api_service.dart';

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> with SingleTickerProviderStateMixin {
  late MobileScannerController _scannerController;
  late AnimationController _animationController;

  String _fullName = '';
  String _studentId = '';
  String _nationalId = '';
  String _deviceUuid = '';
  bool _isLoadingStudent = true;
  bool _isProcessingScan = false;

  @override
  void initState() {
    super.initState();
    _loadStudentProfile();

    // MobileScanner with camera-only live scanning, no gallery picker allowed
    _scannerController = MobileScannerController(
      detectionSpeed: DetectionSpeed.normal,
      facing: CameraFacing.back,
      torchEnabled: false,
    );

    // Laser scanning animation
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _animationController.dispose();
    _scannerController.dispose();
    super.dispose();
  }

  Future<void> _loadStudentProfile() async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    setState(() {
      _fullName = prefs.getString('full_name') ?? 'Enrolled Student';
      _studentId = prefs.getString('student_id') ?? 'N/A';
      _nationalId = prefs.getString('national_id') ?? 'N/A';
      _deviceUuid = prefs.getString('device_uuid') ?? 'UNBOUND';
      _isLoadingStudent = false;
    });
  }

  void _onBarcodeDetected(BarcodeCapture capture) {
    if (_isProcessingScan) return;

    final List<Barcode> barcodes = capture.barcodes;
    if (barcodes.isEmpty) return;

    final String? rawValue = barcodes.first.rawValue;
    if (rawValue == null || rawValue.isEmpty) return;

    _processQrPayload(rawValue);
  }

  Future<void> _processQrPayload(String rawPayload) async {
    if (_isProcessingScan) return;
    setState(() {
      _isProcessingScan = true;
    });

    try {
      // Decode QR code payload format:
      // {"server_url": "http://192.168.1.50:8000/api/attend", "token": "<16_CHAR_TOKEN>"}
      final Map<String, dynamic> data = jsonDecode(rawPayload);
      final String? serverUrl = data['server_url'] as String?;
      final String? token = data['token'] as String?;

      if (serverUrl == null || token == null || token.isEmpty) {
        _showInvalidQrDialog('QR code does not contain a valid server endpoint or token.');
        return;
      }

      // Show temporary processing hud
      _dispatchAttendance(serverUrl, token);
    } catch (_) {
      _showInvalidQrDialog('Invalid QR code format. Expected JSON payload with server_url and token.');
    }
  }

  Future<void> _dispatchAttendance(String serverUrl, String token) async {
    final result = await ApiService.submitAttendance(
      serverUrl: serverUrl,
      token: token,
      studentId: _studentId,
      nationalId: _nationalId,
      fullName: _fullName,
      deviceUuid: _deviceUuid,
    );

    if (!mounted) return;

    _showResultDialog(result);
  }

  void _showResultDialog(AttendanceResponse response) {
    switch (response.status) {
      case AttendanceStatus.success:
        _showDialog(
          title: 'Attendance Confirmed',
          content: 'Student: $_fullName\nID: $_studentId\nStatus: Check-in successfully registered.',
          backgroundColor: const Color(0xFF065F46),
          icon: Icons.check_circle_rounded,
          iconColor: const Color(0xFF34D399),
        );
        break;

      case AttendanceStatus.antiProxyConflict:
        _showDialog(
          title: 'Anti-Proxy Alert',
          content: 'Anti-Proxy Alert: This device has already logged attendance for another student!',
          backgroundColor: const Color(0xFF7F1D1D),
          icon: Icons.security_update_warning_rounded,
          iconColor: const Color(0xFFF87171),
        );
        break;

      case AttendanceStatus.expiredToken:
        _showDialog(
          title: 'Expired Token',
          content: 'Expired QR token. Please scan the current code on screen.',
          backgroundColor: const Color(0xFF7F1D1D),
          icon: Icons.timer_off_rounded,
          iconColor: const Color(0xFFF87171),
        );
        break;

      case AttendanceStatus.networkError:
      default:
        _showDialog(
          title: 'Connection Failure',
          content: 'Cannot reach server. Ensure you are connected to the lecture Wi-Fi.',
          backgroundColor: const Color(0xFF78350F),
          icon: Icons.wifi_off_rounded,
          iconColor: const Color(0xFFFBBF24),
        );
        break;
    }
  }

  void _showInvalidQrDialog(String message) {
    _showDialog(
      title: 'Invalid QR Code',
      content: message,
      backgroundColor: const Color(0xFF78350F),
      icon: Icons.qr_code_scanner_rounded,
      iconColor: const Color(0xFFFBBF24),
    );
  }

  void _showDialog({
    required String title,
    required String content,
    required Color backgroundColor,
    required IconData icon,
    required Color iconColor,
  }) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext ctx) {
        return AlertDialog(
          backgroundColor: backgroundColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(color: iconColor.withOpacity(0.5), width: 1.5),
          ),
          icon: Icon(icon, size: 52, color: iconColor),
          title: Text(
            title,
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
          ),
          content: Text(
            content,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white, fontSize: 14.5, height: 1.4),
          ),
          actionsAlignment: MainAxisAlignment.center,
          actions: [
            ElevatedButton(
              onPressed: () {
                Navigator.of(ctx).pop();
                setState(() {
                  _isProcessingScan = false;
                });
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.white,
                foregroundColor: Colors.black87,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              ),
              child: const Text('Dismiss', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // 1. Live Camera Barcode Scanner (ML Kit on-device barcode detector)
          Positioned.fill(
            child: MobileScanner(
              controller: _scannerController,
              onDetect: _onBarcodeDetected,
            ),
          ),

          // 2. Viewfinder Target Overlay
          Positioned.fill(
            child: AnimatedBuilder(
              animation: _animationController,
              builder: (context, child) {
                return CustomPaint(
                  painter: ViewfinderOverlayPainter(
                    animationProgress: _animationController.value,
                  ),
                );
              },
            ),
          ),

          // 3. Top Intranet Status & Instructions Banner
          SafeArea(
            child: Align(
              alignment: Alignment.topCenter,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: const Color(0xFF0F172A).withOpacity(0.92),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: const Color(0xFF334155)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 10,
                      height: 10,
                      decoration: const BoxDecoration(
                        color: Color(0xFF10B981),
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Local Wi-Fi Intranet Active',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: 16),
                    IconButton(
                      icon: const Icon(Icons.flash_on, color: Colors.white, size: 20),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                      onPressed: () => _scannerController.toggleTorch(),
                      tooltip: 'Toggle Flashlight',
                    ),
                  ],
                ),
              ),
            ),
          ),

          // 4. Processing overlay
          if (_isProcessingScan)
            Positioned.fill(
              child: Container(
                color: Colors.black54,
                child: const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: Color(0xFF60A5FA)),
                      SizedBox(height: 16),
                      Text(
                        'Dispatching attendance verification...',
                        style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                ),
              ),
            ),

          // 5. Bottom Read-Only Student Ribbon
          Align(
            alignment: Alignment.bottomCenter,
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
              decoration: BoxDecoration(
                color: const Color(0xFF0F172A).withOpacity(0.95),
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
                border: const Border(
                  top: BorderSide(color: Color(0xFF334155), width: 1.5),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Colors.black54,
                    blurRadius: 16,
                    offset: Offset(0, -4),
                  ),
                ],
              ),
              child: SafeArea(
                top: false,
                child: _isLoadingStudent
                    ? const Center(child: CircularProgressIndicator())
                    : Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 44,
                                height: 44,
                                decoration: BoxDecoration(
                                  color: const Color(0xFF1E3A8A),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: const Icon(
                                  Icons.badge_rounded,
                                  color: Color(0xFF60A5FA),
                                  size: 26,
                                ),
                              ),
                              const SizedBox(width: 14),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      _fullName,
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      'Student ID: $_studentId',
                                      style: TextStyle(
                                        color: Colors.grey.shade400,
                                        fontSize: 13,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF10B981).withOpacity(0.15),
                                  borderRadius: BorderRadius.circular(8),
                                  border: Border.all(color: const Color(0xFF10B981).withOpacity(0.4)),
                                ),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.lock, size: 12, color: Color(0xFF34D399)),
                                    SizedBox(width: 4),
                                    Text(
                                      'BOUND',
                                      style: TextStyle(
                                        color: Color(0xFF34D399),
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                        letterSpacing: 0.5,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: const Color(0xFF1E293B),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              'Device UUID: ${_deviceUuid.length > 20 ? "${_deviceUuid.substring(0, 18)}..." : _deviceUuid}',
                              style: TextStyle(
                                color: Colors.grey.shade400,
                                fontSize: 11,
                                fontFamily: 'monospace',
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ),
                        ],
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Custom painter rendering the QR target viewfinder overlay and the sweeping laser scan beam
class ViewfinderOverlayPainter extends CustomPainter {
  final double animationProgress;

  ViewfinderOverlayPainter({required this.animationProgress});

  @override
  void paint(Canvas canvas, Size size) {
    const double scanAreaSize = 260.0;
    final double left = (size.width - scanAreaSize) / 2;
    final double top = (size.height - scanAreaSize) / 2 - 40;
    final Rect scanRect = Rect.fromLTWH(left, top, scanAreaSize, scanAreaSize);

    // Darkened surrounding background
    final Paint backgroundPaint = Paint()..color = Colors.black.withOpacity(0.55);
    final Path backgroundPath = Path()
      ..addRect(Rect.fromLTWH(0, 0, size.width, size.height))
      ..addRRect(RRect.fromRectAndRadius(scanRect, const Radius.circular(16)))
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(backgroundPath, backgroundPaint);

    // Viewfinder corner brackets
    final Paint cornerPaint = Paint()
      ..color = const Color(0xFF3B82F6)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.0
      ..strokeCap = StrokeCap.round;

    const double cornerLength = 28.0;

    // Top-Left
    canvas.drawLine(Offset(left, top + cornerLength), Offset(left, top + 8), cornerPaint);
    canvas.drawLine(Offset(left + 8, top), Offset(left + cornerLength, top), cornerPaint);

    // Top-Right
    canvas.drawLine(Offset(left + scanAreaSize - cornerLength, top), Offset(left + scanAreaSize - 8, top), cornerPaint);
    canvas.drawLine(Offset(left + scanAreaSize, top + 8), Offset(left + scanAreaSize, top + cornerLength), cornerPaint);

    // Bottom-Left
    canvas.drawLine(Offset(left, top + scanAreaSize - cornerLength), Offset(left, top + scanAreaSize - 8), cornerPaint);
    canvas.drawLine(Offset(left + 8, top + scanAreaSize), Offset(left + cornerLength, top + scanAreaSize), cornerPaint);

    // Bottom-Right
    canvas.drawLine(Offset(left + scanAreaSize - cornerLength, top + scanAreaSize), Offset(left + scanAreaSize - 8, top + scanAreaSize), cornerPaint);
    canvas.drawLine(Offset(left + scanAreaSize, top + scanAreaSize - cornerLength), Offset(left + scanAreaSize, top + scanAreaSize - 8), cornerPaint);

    // Sweeping laser beam
    final double beamY = top + (scanAreaSize * animationProgress);
    final Paint laserPaint = Paint()
      ..shader = LinearGradient(
        colors: [
          Colors.transparent,
          const Color(0xFF60A5FA).withOpacity(0.8),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(left, beamY - 2, scanAreaSize, 4))
      ..strokeWidth = 3.0;

    canvas.drawLine(Offset(left + 8, beamY), Offset(left + scanAreaSize - 8, beamY), laserPaint);
  }

  @override
  bool shouldRepaint(covariant ViewfinderOverlayPainter oldDelegate) {
    return oldDelegate.animationProgress != animationProgress;
  }
}
