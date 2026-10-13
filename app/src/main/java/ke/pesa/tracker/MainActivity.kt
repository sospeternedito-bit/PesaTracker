package ke.pesa.tracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var prompting = false

    inner class Bridge {
        @JavascriptInterface
        fun takePending(): String {
            val p = getSharedPreferences("pesa", Context.MODE_PRIVATE)
            val s = p.getString("q", "[]") ?: "[]"
            p.edit().remove("q").apply()
            return s
        }

        @JavascriptInterface
        fun popupOn(): Boolean =
            getSharedPreferences("pesa", Context.MODE_PRIVATE).getBoolean("popup", false) &&
                Settings.canDrawOverlays(this@MainActivity)

        @JavascriptInterface
        fun setPopup(on: Boolean) {
            getSharedPreferences("pesa", Context.MODE_PRIVATE).edit().putBoolean("popup", on).apply()
            if (on && !Settings.canDrawOverlays(this@MainActivity)) {
                runOnUiThread {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }

        @JavascriptInterface
        fun canBio(): Boolean {
            if (Build.VERSION.SDK_INT < 29) return false
            val m = this@MainActivity.getSystemService(BiometricManager::class.java) ?: return false
            return if (Build.VERSION.SDK_INT >= 30)
                m.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            else
                m.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }

        @JavascriptInterface
        fun bio() {
            runOnUiThread { showBio() }
        }
    }

    private fun sendBio(ok: Boolean) {
        web.evaluateJavascript("window.bioResult&&bioResult($ok)", null)
    }

    private fun showBio() {
        if (Build.VERSION.SDK_INT < 28 || prompting) return
        prompting = true
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Unlock Pesa Tracker")
            .setNegativeButton("Use PIN", mainExecutor) { _, _ ->
                prompting = false
                sendBio(false)
            }
            .build()
        prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                prompting = false
                sendBio(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                prompting = false
                sendBio(false)
            }
        })
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#2563EB")
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")

        val need = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        web.evaluateJavascript("window.checkPending&&checkPending();window.refreshMore&&refreshMore()", null)
    }
}
