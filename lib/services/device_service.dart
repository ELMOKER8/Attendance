import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

class DeviceService {
  static const String _keyDeviceUuid = 'device_uuid';
  static const FlutterSecureStorage _secureStorage = FlutterSecureStorage();
  static final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();
  static const Uuid _uuid = Uuid();

  /// Retrieves or derives a permanent, hardware-bound device UUID (UUID v5).
  /// This enforces a strict 1:1 student-to-device relationship (Anti-Proxy).
  static Future<String> getDeviceUuid() async {
    // 1. Check if UUID is already saved in secure storage
    final String? existingUuid = await _secureStorage.read(key: _keyDeviceUuid);
    if (existingUuid != null && existingUuid.isNotEmpty) {
      return existingUuid;
    }

    // 2. Read hardware parameters from Android system
    String hardwareSeed = 'unknown-device';
    try {
      final AndroidDeviceInfo androidInfo = await _deviceInfo.androidInfo;
      // Combine id, hardware, and brand to form the hardware fingerprint seed
      final String id = androidInfo.id;
      final String hardware = androidInfo.hardware;
      final String brand = androidInfo.brand;
      hardwareSeed = '$brand:$hardware:$id';
    } catch (_) {
      // Fallback seed if hardware info extraction fails
      hardwareSeed = 'hardware-fallback-${DateTime.now().millisecondsSinceEpoch}';
    }

    // 3. Generate deterministic UUID v5 using NAMESPACE_OID and hardware seed
    final String derivedUuid = _uuid.v5(Uuid.NAMESPACE_OID, hardwareSeed);

    // 4. Store permanently in secure storage
    await _secureStorage.write(key: _keyDeviceUuid, value: derivedUuid);

    return derivedUuid;
  }
}
