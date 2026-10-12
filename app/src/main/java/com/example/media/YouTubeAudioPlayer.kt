package com.example.media

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class YouTubeAudioPlayer(
    private val context: Context,
    private val onPlaybackStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    private val onPositionUpdated: (positionMs: Long, durationMs: Long) -> Unit,
    private val onTrackEnded: () -> Unit,
    private val onPlayerError: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    var webView: WebView? = null
        private set

    private var isPlayerReady = false
    private var pendingVideoId: String? = null
    var isCurrentlyPlaying = false
        private set

    init {
        mainHandler.post {
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        try {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e("YouTubeAudioPlayer", "WebView error: ${error?.description}")
                    }
                }
                addJavascriptInterface(PlayerBridge(), "AndroidBridge")
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <style>
                        html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
                        #player { width: 100%; height: 100%; }
                    </style>
                </head>
                <body>
                    <div id="player"></div>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                        var player;
                        var timeInterval = null;

                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                height: '100%',
                                width: '100%',
                                playerVars: {
                                    'autoplay': 1,
                                    'controls': 0,
                                    'playsinline': 1,
                                    'rel': 0,
                                    'showinfo': 0,
                                    'disablekb': 1,
                                    'modestbranding': 1,
                                    'fs': 0,
                                    'origin': 'https://www.youtube.com'
                                },
                                events: {
                                    'onReady': onPlayerReady,
                                    'onStateChange': onPlayerStateChange,
                                    'onError': onPlayerError
                                }
                            });
                        }

                        function onPlayerReady(event) {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onReady();
                            }
                        }

                        function onPlayerStateChange(event) {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onStateChange(event.data);
                            }
                            if (event.data === 1) { // PLAYING
                                startTimeTracker();
                            } else {
                                stopTimeTracker();
                            }
                        }

                        function onPlayerError(event) {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onError(event.data);
                            }
                        }

                        function startTimeTracker() {
                            stopTimeTracker();
                            timeInterval = setInterval(function() {
                                if (player && player.getCurrentTime && player.getDuration) {
                                    var current = player.getCurrentTime();
                                    var dur = player.getDuration();
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.onTimeUpdate(current, dur);
                                    }
                                }
                            }, 300);
                        }

                        function stopTimeTracker() {
                            if (timeInterval) {
                                clearInterval(timeInterval);
                                timeInterval = null;
                            }
                        }

                        function playVideo(id) {
                            if (player && player.loadVideoById) {
                                player.loadVideoById(id);
                                player.playVideo();
                            }
                        }

                        function play() {
                            if (player && player.playVideo) {
                                player.playVideo();
                            }
                        }

                        function pause() {
                            if (player && player.pauseVideo) {
                                player.pauseVideo();
                            }
                        }

                        function seekTo(seconds) {
                            if (player && player.seekTo) {
                                player.seekTo(seconds, true);
                            }
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            webView = wv
        } catch (e: Exception) {
            Log.e("YouTubeAudioPlayer", "Failed to init WebView", e)
        }
    }

    fun play(videoId: String) {
        mainHandler.post {
            if (!isPlayerReady) {
                pendingVideoId = videoId
            } else {
                webView?.evaluateJavascript("playVideo('$videoId');", null)
            }
        }
    }

    fun resume() {
        mainHandler.post {
            webView?.evaluateJavascript("play();", null)
        }
    }

    fun pause() {
        mainHandler.post {
            webView?.evaluateJavascript("pause();", null)
        }
    }

    fun seekTo(seconds: Float) {
        mainHandler.post {
            webView?.evaluateJavascript("seekTo($seconds);", null)
        }
    }

    private inner class PlayerBridge {
        @JavascriptInterface
        fun onReady() {
            mainHandler.post {
                isPlayerReady = true
                pendingVideoId?.let { id ->
                    pendingVideoId = null
                    webView?.evaluateJavascript("playVideo('$id');", null)
                }
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int) {
            mainHandler.post {
                when (state) {
                    1 -> { // PLAYING
                        isCurrentlyPlaying = true
                        onPlaybackStateChanged(true, false)
                    }
                    2 -> { // PAUSED
                        isCurrentlyPlaying = false
                        onPlaybackStateChanged(false, false)
                    }
                    3 -> { // BUFFERING
                        onPlaybackStateChanged(isCurrentlyPlaying, true)
                    }
                    0 -> { // ENDED
                        isCurrentlyPlaying = false
                        onPlaybackStateChanged(false, false)
                        onTrackEnded()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSec: Float, durationSec: Float) {
            mainHandler.post {
                val currentMs = (currentSec * 1000).toLong()
                val durationMs = (durationSec * 1000).toLong()
                onPositionUpdated(currentMs, durationMs)
            }
        }

        @JavascriptInterface
        fun onError(errorCode: Int) {
            mainHandler.post {
                Log.e("YouTubeAudioPlayer", "YouTube Player Error: $errorCode")
                isCurrentlyPlaying = false
                onPlaybackStateChanged(false, false)
                onPlayerError("YouTube stream error code $errorCode")
            }
        }
    }

    fun release() {
        mainHandler.post {
            webView?.evaluateJavascript("pause();", null)
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}
