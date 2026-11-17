package com.dermoai.feature.finder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.dermoai.feature.finder.data.Clinic
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenStreetMap + Leaflet, rendered in a WebView.
 *
 * Leaflet and the map page ship inside the app (assets/finder), so only the map
 * tiles come from the network. The page is served from a synthetic https origin
 * so that tile requests carry a Referer, which OpenStreetMap's tile policy asks for.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LeafletMap(
    userLat: Double,
    userLon: Double,
    clinics: List<Clinic>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val youLabel = stringResource(R.string.finder_you)
    val tilesError = stringResource(R.string.finder_tiles_error)

    val webView = remember {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect in debug builds only
        }
        WebView(context).apply {
            settings.javaScriptEnabled = true // Leaflet is JavaScript
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " DermoAI/2.0"
            setBackgroundColor(0xFFE9E4DA.toInt())
            webViewClient = MapWebViewClient(context)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.d(TAG, "js: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(
                FinderJsBridge(
                    onReady = { ready = true },
                    onSelected = { id -> currentOnSelect(id) },
                ),
                "Android",
            )
            // The page and Leaflet are read from assets and passed in as one document, so no
            // request interception is needed; the base URL gives tile requests a Referer.
            loadDataWithBaseURL(BASE_URL, buildMapHtml(context), "text/html", "utf-8", null)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("Android")
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // Push the user + clinics into the page whenever they change (and once the page is ready).
    LaunchedEffect(ready, userLat, userLon, clinics, youLabel, tilesError) {
        if (!ready) return@LaunchedEffect
        val payload = JSONObject()
            .put("strings", JSONObject().put("you", youLabel).put("tilesError", tilesError))
            .put("user", JSONObject().put("lat", userLat).put("lon", userLon))
            .put(
                "clinics",
                JSONArray().also { arr ->
                    // Names are user-editable OSM data; the page only ever uses them as text.
                    clinics.take(MAX_MARKERS).forEach { c ->
                        arr.put(
                            JSONObject()
                                .put("id", c.id).put("name", c.name)
                                .put("lat", c.lat).put("lon", c.lon),
                        )
                    }
                },
            )
        webView.evaluateJavascript("dermo.setData($payload);", null)
    }

    LaunchedEffect(ready, selectedId) {
        if (!ready) return@LaunchedEffect
        val arg = if (selectedId == null) "null" else JSONObject.quote(selectedId)
        webView.evaluateJavascript("dermo.select($arg);", null)
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private const val TAG = "DermoFinder"
private const val HOST = "appassets.androidplatform.net"
private const val BASE_URL = "https://$HOST/finder/"
private const val MAX_MARKERS = 40

private fun Context.readAsset(name: String): String =
    assets.open("finder/$name").bufferedReader().use { it.readText() }

/** map.html with leaflet.css and leaflet.js inlined, so the whole map ships as one document. */
private fun buildMapHtml(context: Context): String =
    context.readAsset("map.html")
        .replace(
            "<link rel=\"stylesheet\" href=\"leaflet.css\">",
            "<style>\n" + context.readAsset("leaflet.css") + "\n</style>",
        )
        .replace(
            "<script src=\"leaflet.js\"></script>",
            "<script>\n" + context.readAsset("leaflet.js") + "\n</script>",
        )

/** Called by the page on a background thread; hop to the main thread before touching Compose state. */
private class FinderJsBridge(
    private val onReady: () -> Unit,
    private val onSelected: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapReady() {
        main.post { onReady() }
    }

    @JavascriptInterface
    fun onClinicSelected(id: String) {
        main.post { onSelected(id) }
    }
}

private class MapWebViewClient(private val context: Context) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        Log.d(TAG, "map page started")
    }

    override fun onPageFinished(view: WebView, url: String?) {
        Log.d(TAG, "map page finished")
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        Log.w(TAG, "web error ${error.errorCode} ${error.description} for ${request.url}")
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        Log.w(TAG, "web http ${errorResponse.statusCode} for ${request.url}")
    }

    /** Links inside the map (e.g. the © OpenStreetMap credit) open in the browser, never in the map view. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        // The map page's own address must load in the view; only links to other sites leave it.
        if (uri.host == HOST) return false
        if (uri.scheme == "https" || uri.scheme == "http") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return true
    }
}
