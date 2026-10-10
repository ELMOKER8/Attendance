package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ApiService
import com.example.service.AttendanceResult
import com.example.service.StorageService
import com.example.service.StudentProfile
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Blue400
import com.example.ui.theme.Blue500
import com.example.ui.theme.Blue600
import com.example.ui.theme.Blue900
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Red400
import com.example.ui.theme.Red700
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var isDispatching by remember { mutableStateOf(false) }

    // Dialog State
    var activeDialogResult by remember { mutableStateOf<AttendanceResult?>(null) }

    // Simulator modal for test QR codes
    var showSimulatorDialog by remember { mutableStateOf(false) }
    var testServerUrl by remember { mutableStateOf("http://192.168.1.50:8000/api/attend") }
    var testToken by remember { mutableStateOf("TK_99812487612301") }

    LaunchedEffect(Unit) {
        profile = StorageService.getStudentProfile(context)
    }

    // Laser scanning animation transition
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    fun handleQrPayload(rawPayload: String) {
        if (isDispatching) return
        val currentProfile = profile ?: return

        isDispatching = true
        coroutineScope.launch {
            try {
                val json = JSONObject(rawPayload)
                val serverUrl = json.optString("server_url", "")
                val token = json.optString("token", "")

                if (serverUrl.isEmpty() || token.isEmpty()) {
                    activeDialogResult = AttendanceResult.InvalidPayload(
                        "QR code does not contain a valid server endpoint or token."
                    )
                    isDispatching = false
                    return@launch
                }

                val result = ApiService.submitAttendance(
                    serverUrl = serverUrl,
                    token = token,
                    studentId = currentProfile.studentId,
                    nationalId = currentProfile.nationalId,
                    fullName = currentProfile.fullName,
                    deviceUuid = currentProfile.deviceUuid
                )
                activeDialogResult = result
            } catch (e: Exception) {
                activeDialogResult = AttendanceResult.InvalidPayload(
                    "Invalid QR format. Expected JSON payload: {\"server_url\": \"...\", \"token\": \"...\"}"
                )
            } finally {
                isDispatching = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Viewfinder Target Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val scanSize = 270.dp.toPx()
            val left = (canvasWidth - scanSize) / 2
            val top = (canvasHeight - scanSize) / 2 - 40.dp.toPx()
            val right = left + scanSize
            val bottom = top + scanSize

            // Darken surround
            val path = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(path = path, color = Color(0xAA0A0F1D))

            // Corner Brackets
            val cornerLen = 32.dp.toPx()
            val strokeW = 4.dp.toPx()
            val cornerColor = Color(0xFF60A5FA)

            // Top-Left
            drawLine(cornerColor, Offset(left, top + cornerLen), Offset(left, top + 10f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(left + 10f, top), Offset(left + cornerLen, top), strokeWidth = strokeW, cap = StrokeCap.Round)

            // Top-Right
            drawLine(cornerColor, Offset(right - cornerLen, top), Offset(right - 10f, top), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(right, top + 10f), Offset(right, top + cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)

            // Bottom-Left
            drawLine(cornerColor, Offset(left, bottom - cornerLen), Offset(left, bottom - 10f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(left + 10f, bottom), Offset(left + cornerLen, bottom), strokeWidth = strokeW, cap = StrokeCap.Round)

            // Bottom-Right
            drawLine(cornerColor, Offset(right - cornerLen, bottom), Offset(right - 10f, bottom), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(right, bottom - 10f), Offset(right, bottom - cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)

            // Sweeping Laser Beam
            val beamY = top + (scanSize * laserProgress)
            val laserBrush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color(0xFF60A5FA).copy(alpha = 0.85f), Color.Transparent),
                start = Offset(left, beamY),
                end = Offset(right, beamY)
            )
            drawLine(
                brush = laserBrush,
                start = Offset(left + 12f, beamY),
                end = Offset(right - 12f, beamY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 2. Top Wi-Fi Intranet Status Ribbon
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                color = Slate900.copy(alpha = 0.94f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local Wi-Fi Intranet Active",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    // Test Simulator Button
                    IconButton(
                        onClick = { showSimulatorDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("simulate_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Simulate Scan",
                            tint = Blue400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Center Viewfinder Prompt Text
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 170.dp)
        ) {
            Text(
                text = "Align instructor's QR code within viewfinder",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Slate200.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
        }

        // 3. Dispatching Progress Overlay
        if (isDispatching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Blue400,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Dispatching attendance verification...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // 4. Bottom Read-Only Student Ribbon
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Slate900.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = Slate700,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            val student = profile
            if (student != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Blue900),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = Blue400,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Student ID: ${student.studentId}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Slate400
                                )
                            )
                        }

                        // Anti-Proxy Bound Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BOUND",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hardware UUID Snippet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Device UUID: ${student.deviceUuid}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Slate400,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                CircularProgressIndicator(
                    color = Blue400,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Response Dialogs for All 4 Cases
    activeDialogResult?.let { result ->
        when (result) {
            is AttendanceResult.Success -> {
                AlertDialog(
                    onDismissRequest = { activeDialogResult = null },
                    containerColor = Color(0xFF064E3B),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Attendance Confirmed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = "Student: ${result.fullName}\nStatus: ${result.message}",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeDialogResult = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF064E3B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_dismiss_button")
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
            is AttendanceResult.AntiProxyConflict -> {
                AlertDialog(
                    onDismissRequest = { activeDialogResult = null },
                    containerColor = Color(0xFF7F1D1D),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Anti-Proxy Alert",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = result.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeDialogResult = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF7F1D1D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_dismiss_button")
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
            is AttendanceResult.ExpiredToken -> {
                AlertDialog(
                    onDismissRequest = { activeDialogResult = null },
                    containerColor = Color(0xFF7F1D1D),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Expired QR Token",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = result.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeDialogResult = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF7F1D1D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_dismiss_button")
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
            is AttendanceResult.NetworkError -> {
                AlertDialog(
                    onDismissRequest = { activeDialogResult = null },
                    containerColor = Color(0xFF78350F),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Network Error",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = result.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeDialogResult = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF78350F)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_dismiss_button")
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
            is AttendanceResult.InvalidPayload -> {
                AlertDialog(
                    onDismissRequest = { activeDialogResult = null },
                    containerColor = Slate800,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Invalid QR Code",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = result.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeDialogResult = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue600,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }

    // Test Simulator Dialog for QR testing inside emulator/review
    if (showSimulatorDialog) {
        AlertDialog(
            onDismissRequest = { showSimulatorDialog = false },
            containerColor = Slate900,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "QR Scan Simulator",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Simulate scanning an on-screen lecture QR code inside the isolated Wi-Fi intranet:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = testServerUrl,
                        onValueChange = { testServerUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate800,
                            unfocusedContainerColor = Slate800,
                            focusedBorderColor = Blue400,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sim_server_url")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testToken,
                        onValueChange = { testToken = it },
                        label = { Text("16-Char QR Token") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate800,
                            unfocusedContainerColor = Slate800,
                            focusedBorderColor = Blue400,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sim_token")
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Slate400, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSimulatorDialog = false
                                val currentStudent = profile?.fullName ?: "Student"
                                activeDialogResult = AttendanceResult.Success(
                                    fullName = currentStudent,
                                    message = "Attendance recorded successfully."
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sim 200", fontSize = 11.sp, color = Color(0xFF34D399))
                        }
                        OutlinedButton(
                            onClick = {
                                showSimulatorDialog = false
                                activeDialogResult = AttendanceResult.AntiProxyConflict(
                                    "Anti-Proxy Alert: This device has already logged attendance for another student!"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sim 409", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                        OutlinedButton(
                            onClick = {
                                showSimulatorDialog = false
                                activeDialogResult = AttendanceResult.ExpiredToken(
                                    "Expired QR token. Please scan the current code on screen."
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sim 401", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSimulatorDialog = false
                        val payload = JSONObject().apply {
                            put("server_url", testServerUrl)
                            put("token", testToken)
                        }.toString()
                        handleQrPayload(payload)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue600,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dispatch_sim_scan_button")
                ) {
                    Text("Dispatch HTTP POST")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSimulatorDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}
