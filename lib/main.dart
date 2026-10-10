import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/onboarding_screen.dart';
import 'screens/scanner_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Enforce portrait orientation for scanner stability
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
  ]);

  // Check enrollment status
  final SharedPreferences prefs = await SharedPreferences.getInstance();
  final bool isEnrolled = prefs.getBool('is_enrolled') ?? false;

  runApp(AttendanceApp(isEnrolled: isEnrolled));
}

class AttendanceApp extends StatelessWidget {
  final bool isEnrolled;

  const AttendanceApp({super.key, required this.isEnrolled});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Student Attendance',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF3B82F6),
          secondary: Color(0xFF60A5FA),
          surface: Color(0xFF1E293B),
        ),
        fontFamily: 'Roboto',
        useMaterial3: true,
      ),
      home: isEnrolled ? const ScannerScreen() : const OnboardingScreen(),
    );
  }
}
