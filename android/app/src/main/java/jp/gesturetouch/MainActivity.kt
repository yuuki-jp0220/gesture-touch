package jp.gesturetouch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var pendingCameraRequest: PermissionRequest? = null
    private val cameraRequestCode = 501
    private val pageUrl = "https://yuuki-jp0220.github.io/gesture-touch/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 0)
        }
        status = TextView(this).apply {
            text = "連携状態を確認中…"
            textSize = 13f
            setPadding(4, 4, 4, 8)
        }
        val settingsButton = Button(this).apply {
            text = "Androidのユーザー補助設定を開く"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(settingsButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.addJavascriptInterface(GestureBridge(), "GestureTouchBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return if (uri.scheme == "https" && uri.host == "yuuki-jp0220.github.io") {
                    false
                } else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                    true
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (wantsCamera && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else if (wantsCamera) {
                        pendingCameraRequest = request
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraRequestCode)
                    } else {
                        request.deny()
                    }
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraRequestCode)
        }
        webView.loadUrl(pageUrl)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun updateStatus() {
        status.text = if (GestureAccessibilityService.isEnabled) {
            "Android連携：ON（上下フリックを画面操作に変換）"
        } else {
            "Android連携：OFF（上のボタンからユーザー補助を有効にしてください）"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraRequestCode) {
            val request = pendingCameraRequest
            pendingCameraRequest = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                request?.deny()
                Toast.makeText(this, "カメラの許可が必要です", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    inner class GestureBridge {
        @JavascriptInterface
        fun performGesture(kind: String) {
            if (kind != "SWIPE_UP" && kind != "SWIPE_DOWN") return
            val accepted = GestureAccessibilityService.perform(kind)
            runOnUiThread {
                status.text = if (accepted) "Android連携：$kind を送信" else
                    "Android連携：OFF（ユーザー補助を有効にしてください）"
            }
        }
    }
}
