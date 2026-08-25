package com.yourcompany.orderform

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.orderFormWebView)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        // Decouples WebView text rendering from the device's system
        // font-size accessibility setting (confirmed root cause of the
        // 1.75x on-screen/PDF scaling issue).
        settings.textZoom = 100
        // Lets the page's existing window.print() call reach Android's print system
        webView.addJavascriptInterface(PrintBridge(this), "AndroidPrint")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                return !url.startsWith("file:///android_asset/order_form.html")
            }
            // Once the page has finished loading, replace window.print()
            // with a version that calls into Android's native print system.
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(
                    "window.print = function() { AndroidPrint.requestPrint(); };",
                    null
                )
            }
        }
        // Handles JS dialogs (confirm/alert/prompt) that the page relies on,
        // e.g. the "New Form" button's window.confirm(). Without this,
        // WebView silently suppresses JS dialogs and confirm() always
        // returns false.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }
        }
        webView.loadUrl("file:///android_asset/order_form.html")
    }
    // Performs the actual Android print job using the WebView's
    // existing print rendering and @media print CSS.
    fun doWebViewPrint() {
        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Order Form Document"
        val density = resources.displayMetrics.density
        val densityDpi = resources.displayMetrics.densityDpi
        android.widget.Toast.makeText(
            this,
            "WebView scale = ${webView.scale}\nDensity = $density\nDensity DPI = $densityDpi",
            android.widget.Toast.LENGTH_LONG
        ).show()
        val printAdapter =
            webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(
            jobName,
            printAdapter,
            attributes
        )
    }
    override fun onBackPressed() {
        // Intentionally empty.
    }
}
// JavaScript bridge.
// JavaScript calls AndroidPrint.requestPrint(),
// which triggers the Android print system.
class PrintBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun requestPrint() {
        activity.runOnUiThread {
            activity.doWebViewPrint()
        }
    }
}
