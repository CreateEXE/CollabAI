package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewView(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Restricted security configuration:
                // Enable JavaScript for client interactivity, but block file/content scheme access
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }

                // Prevent opening arbitrary external URLs or redirecting outside sandbox
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        // Block external navigation inside the sandbox preview
                        return true
                    }
                }
            }
        },
        update = { webView ->
            // Sandboxed load with about:blank baseURL
            webView.loadDataWithBaseURL(
                "about:blank",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}
