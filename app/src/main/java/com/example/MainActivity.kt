package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private var currentTargetChapterId: String? = null
  private var activeWebView: WebView? = null

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val newChapterId = extractChapterIdFromIntent(intent)
    if (!newChapterId.isNullOrBlank()) {
      currentTargetChapterId = newChapterId
      activeWebView?.post {
        activeWebView?.evaluateJavascript(
          "if (window.openChapterSolution) { window.openChapterSolution('$newChapterId'); }",
          null
        )
      }
    }
  }

  internal fun extractChapterIdFromIntent(intent: Intent?): String? {
    val data = intent?.data ?: return null
    try {
      var chapter = data.getQueryParameter("solution")
        ?: data.getQueryParameter("chapter")
        ?: data.getQueryParameter("chapterId")
        ?: data.getQueryParameter("id")

      if (chapter.isNullOrBlank()) {
        val fragment = data.fragment
        if (fragment != null) {
          if (fragment.startsWith("chapter=")) {
            chapter = fragment.substringAfter("chapter=")
          } else if (fragment.startsWith("solution=")) {
            chapter = fragment.substringAfter("solution=")
          }
        }
      }

      if (chapter.isNullOrBlank()) {
        val segments = data.pathSegments
        if (segments.size >= 2 && (segments[0] == "solution" || segments[0] == "chapter")) {
          chapter = segments[1]
        }
      }

      return chapter?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      Log.e("MainActivity", "Error parsing intent data: ${e.message}")
      return null
    }
  }

  private fun getClipboardChapterId(): String? {
    try {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
      if (!clipboard.hasPrimaryClip()) return null
      val item = clipboard.primaryClip?.getItemAt(0) ?: return null
      val text = item.text?.toString() ?: return null

      // Check if text is a solution link or contains solution ID
      if (text.contains("solution=") || text.contains("chapter=") || text.contains("qnashiksha") || text.contains("ais-pre-") || text.contains("run.app")) {
        val regex = Regex("""[?&#](?:solution|chapter|chapterId|id)=([a-zA-Z0-9_\-]+)""")
        val match = regex.find(text)
        if (match != null) {
          return match.groupValues[1]
        }
        val qnaRegex = Regex("""qnashiksha://[^\s]*[?&]id=([a-zA-Z0-9_\-]+)""")
        val qnaMatch = qnaRegex.find(text)
        if (qnaMatch != null) {
          return qnaMatch.groupValues[1]
        }
      }
    } catch (_: Exception) {}
    return null
  }

  inner class AndroidBridge(private val activity: Activity) {
    @JavascriptInterface
    fun getInitialChapterId(): String {
      val target = currentTargetChapterId
      currentTargetChapterId = null
      return target ?: ""
    }

    @JavascriptInterface
    fun clearInitialChapterId() {
      currentTargetChapterId = null
    }

    @JavascriptInterface
    fun isAndroidApp(): Boolean = true

    @JavascriptInterface
    fun shareSolutionNative(chapterId: String, title: String, message: String, url: String) {
      shareSolutionNative(title, message, url)
    }

    @JavascriptInterface
    fun shareSolutionNative(title: String, message: String, url: String) {
      activity.runOnUiThread {
        try {
          val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$message\n\n👉 $url")
            type = "text/plain"
          }
          val chooser = Intent.createChooser(sendIntent, "Send NCERT Solution via")
          activity.startActivity(chooser)
        } catch (e: Exception) {
          Log.e("AndroidBridge", "Share failed: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
      activity.runOnUiThread {
        try {
          val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          val clip = ClipData.newPlainText("QnA Shiksha Solution", text)
          clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}
      }
    }

    @JavascriptInterface
    fun isNativeVoiceAvailable(): Boolean {
      return SpeechRecognizer.isRecognitionAvailable(activity)
    }

    @JavascriptInterface
    fun requestMicrophonePermission() {
      activity.runOnUiThread {
        if (ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            2001
          )
        }
      }
    }

    @JavascriptInterface
    fun startNativeVoiceSearch() {
      activity.runOnUiThread {
        if (ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            2001
          )
          return@runOnUiThread
        }

        try {
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
              RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a Class, Subject, or Chapter name...")
          }
          val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
          recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              activeWebView?.post {
                activeWebView?.evaluateJavascript("if (window.onVoiceSearchListening) { window.onVoiceSearchListening(); }", null)
              }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
              activeWebView?.post {
                val errorMsg = when (error) {
                  SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly into your mic."
                  SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
                  SpeechRecognizer.ERROR_NETWORK -> "Network issue for voice recognition."
                  else -> "Speech recognition ended."
                }
                activeWebView?.evaluateJavascript("if (window.onVoiceSearchError) { window.onVoiceSearchError('$errorMsg'); }", null)
              }
            }
            override fun onResults(results: Bundle?) {
              val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val spokenText = matches?.firstOrNull() ?: ""
              if (spokenText.isNotBlank()) {
                val safeText = spokenText.replace("'", "\\'").replace("\n", " ")
                activeWebView?.post {
                  activeWebView?.evaluateJavascript(
                    "if (window.handleVoiceSearchResult) { window.handleVoiceSearchResult('$safeText'); }",
                    null
                  )
                }
              }
            }
            override fun onPartialResults(partialResults: Bundle?) {
              val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val interim = matches?.firstOrNull() ?: return
              val safeText = interim.replace("'", "\\'").replace("\n", " ")
              activeWebView?.post {
                activeWebView?.evaluateJavascript(
                  "if (window.handleVoiceSearchInterim) { window.handleVoiceSearchInterim('$safeText'); }",
                  null
                )
              }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
          })
          recognizer.startListening(intent)
        } catch (e: Exception) {
          Log.e("MainActivity", "Voice recognizer launch failed: ${e.message}")
        }
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Read any incoming deep link or share URI
    currentTargetChapterId = extractChapterIdFromIntent(intent)

    setContent {
      MyApplicationTheme {
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var canGoBack by remember { mutableStateOf(false) }
        var isMobileAdsReady by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
          // Allow WebView and app UI to render first frame immediately without splash delay
          delay(1000)
          try {
            MobileAds.initialize(this@MainActivity) {
              isMobileAdsReady = true
            }
          } catch (e: Exception) {
            Log.e("MainActivity", "AdMob initialization error: ${e.message}")
          }
        }

        BackHandler {
          webViewInstance?.let { wv ->
            wv.evaluateJavascript(
              "(function() { if (window.handleAppBackPress && window.handleAppBackPress()) { return 'handled'; } return 'unhandled'; })();"
            ) { result ->
              val clean = result?.replace("\"", "") ?: ""
              if (clean != "handled") {
                if (wv.canGoBack()) {
                  wv.goBack()
                } else {
                  finish()
                }
              }
            }
          }
        }

        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
          bottomBar = {
            if (isMobileAdsReady) {
              AdMobBanner()
            }
          }
        ) { paddingValues ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues)
          ) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
            factory = { context ->
              WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )

                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  databaseEnabled = true
                  allowFileAccess = true
                  allowContentAccess = true
                  useWideViewPort = false
                  loadWithOverviewMode = false
                  cacheMode = WebSettings.LOAD_DEFAULT
                  mediaPlaybackRequiresUserGesture = false
                  displayZoomControls = false
                  builtInZoomControls = false
                  setSupportZoom(false)
                }

                // Register Javascript bridge for deep link routing & native share
                addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")

                webChromeClient = object : WebChromeClient() {
                  override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                  }

                  override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(
                      "WebViewConsole",
                      "${consoleMessage?.message()} -- Line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
                    )
                    return true
                  }
                }

                webViewClient = object : WebViewClient() {
                  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    canGoBack = view?.canGoBack() == true
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    canGoBack = view?.canGoBack() == true

                    // Dispatch deep link destination directly to the JavaScript web app if present
                    val initChapter = currentTargetChapterId
                    currentTargetChapterId = null
                    if (!initChapter.isNullOrBlank()) {
                      view?.evaluateJavascript(
                        """
                        (function() {
                          if (window.openChapterSolution) {
                            setTimeout(function() {
                              window.openChapterSolution('$initChapter');
                            }, 300);
                          }
                        })();
                        """.trimIndent(),
                        null
                      )
                    }
                  }

                  override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                  ): Boolean {
                    // Allow iframes (e.g. In-App PDF Reader and document previews) to render inside WebView
                    if (request != null && !request.isForMainFrame) {
                      return false
                    }

                    val url = request?.url?.toString() ?: return false
                    // Don't intercept local assets
                    if (url.startsWith("file:///android_asset/")) {
                      return false
                    }

                    // Handle in-app deep link scheme internal redirects
                    if (url.startsWith("qnashiksha://") || url.startsWith("ncert://")) {
                      val uri = Uri.parse(url)
                      val chapterId = uri.getQueryParameter("id")
                        ?: uri.getQueryParameter("solution")
                        ?: uri.getQueryParameter("chapter")
                      if (!chapterId.isNullOrBlank()) {
                        view?.evaluateJavascript(
                          "if (window.openChapterSolution) { window.openChapterSolution('$chapterId'); }",
                          null
                        )
                        return true
                      }
                    }

                    // Handle external links (WhatsApp, Telegram, external PDF download link, etc.)
                    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("whatsapp://") || url.startsWith("tg://") || url.startsWith("mailto:")) {
                      try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        return true
                      } catch (_: Exception) {
                        return false
                      }
                    }
                    return false
                  }
                }

                loadUrl("file:///android_asset/index.html")
                activeWebView = this
                webViewInstance = this
              }
            },
            update = {
              activeWebView = it
              webViewInstance = it
            }
          )
        }
      }
    }
  }
}

  override fun onResume() {
    super.onResume()
    activeWebView?.onResume()
  }

  override fun onPause() {
    super.onPause()
    activeWebView?.onPause()
  }

  override fun onDestroy() {
    activeWebView?.destroy()
    super.onDestroy()
  }
}

/**
 * Google AdMob Adaptive Banner Component.
 * Uses official Google Sample Test Banner Unit ID by default so test ads load immediately.
 * When ready to publish, the user can supply their production AdMob banner Ad Unit ID.
 */
@Composable
fun AdMobBanner(
  modifier: Modifier = Modifier,
  adUnitId: String = "ca-app-pub-3940256099942544/6300978111" // Google Sample Test Banner
) {
  var isAdLoaded by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (isAdLoaded) {
          Modifier
            .wrapContentHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp)
        } else {
          Modifier.height(0.dp)
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      factory = { context ->
        try {
          AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
              override fun onAdLoaded() {
                super.onAdLoaded()
                isAdLoaded = true
                Log.d("AdMob", "AdMob banner loaded successfully")
              }
              override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                isAdLoaded = false
                Log.d("AdMob", "AdMob banner load error: ${error.message}")
              }
            }
            loadAd(AdRequest.Builder().build())
          }
        } catch (t: Throwable) {
          Log.e("AdMob", "Safe catch on AdView creation: ${t.message}")
          android.view.View(context)
        }
      }
    )
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}


