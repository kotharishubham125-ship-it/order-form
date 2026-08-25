package com.yourcompany.orderform

import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
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
        // DIAGNOSTIC TEST (step 2): useWideViewPort now also disabled to
        // isolate whether the wide-viewport layout-width algorithm is
        // contributing to the 1.75 scale. loadWithOverviewMode remains
        // disabled from the previous experiment.
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
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
        webView.loadUrl("file:///android_asset/order_form.html")
    }
    // Performs the actual Android print job using the WebView's
    // existing print rendering and @media print CSS.
    fun doWebViewPrint() {
        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Order Form Document"
        android.widget.Toast.makeText(
            this,
            "WebView scale = ${webView.scale}",
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
