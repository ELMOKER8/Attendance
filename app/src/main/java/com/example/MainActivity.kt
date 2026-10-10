package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.service.StorageService
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.AttendanceTheme
import com.example.ui.theme.Slate900

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Implement process network binding using ConnectivityManager.bindProcessToNetwork
        // to force all network traffic through the Wi-Fi interface, preventing Android from
        // dropping packets or defaulting to mobile data when the Wi-Fi network has no WAN internet.
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                }
            })
        } catch (_: Exception) {
            // Handled safely in restricted or emulator sandbox environments
        }

        setContent {
            AttendanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate900
                ) {
                    var isEnrolled by remember {
                        mutableStateOf(StorageService.isEnrolled(this@MainActivity))
                    }

                    if (isEnrolled) {
                        ScannerScreen()
                    } else {
                        OnboardingScreen(
                            onEnrolled = {
                                isEnrolled = true
                            }
                        )
                    }
                }
            }
        }
    }
}
