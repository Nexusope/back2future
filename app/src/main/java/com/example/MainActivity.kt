package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bridge.AndroidTemporalBridge
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var webViewInstance: WebView? = null
    private var bridgeInstance: AndroidTemporalBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fully edge-to-edge immersive style
        enableEdgeToEdge()
        
        // Dynamic Microphone Request
        checkMicPermission()

        // Modern predictive/gesture-back registration for Android 16+ (API 36+) / SDK 36
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                } else {
                    finish()
                }
            }
        })

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF030712) // Match the custom cosmic slate HTML layout
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    
                                    // Cosmic UI Styling Integration
                                    setBackgroundColor(AndroidColor.TRANSPARENT)
                                    
                                    // Full Hardware Acceleration & Performance Flags
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.loadsImagesAutomatically = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onPermissionRequest(request: PermissionRequest) {
                                            val grants = mutableListOf<String>()
                                            for (res in request.resources) {
                                                if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                        grants.add(res)
                                                    }
                                                } else {
                                                    grants.add(res)
                                                }
                                            }
                                            if (grants.isNotEmpty()) {
                                                request.grant(grants.toTypedArray())
                                            } else {
                                                request.deny()
                                            }
                                        }
                                    }
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            return false
                                        }
                                    }
                                    
                                    // Establish native temporal javascript bridge
                                    val currentBridge = AndroidTemporalBridge(context, this)
                                    bridgeInstance = currentBridge
                                    addJavascriptInterface(currentBridge, "AndroidTemporalBridge")
                                    
                                    webViewInstance = this
                                    
                                    // Load localized high fidelity web layouts assets
                                    loadUrl("file:///android_asset/index.html")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                VOICE_PERMISSION_CODE
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeInstance?.destroy()
    }

    companion object {
        private const val VOICE_PERMISSION_CODE = 9921
    }
}
