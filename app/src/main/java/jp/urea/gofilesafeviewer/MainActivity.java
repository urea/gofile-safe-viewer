package jp.urea.gofilesafeviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String TAG = "GofileSafeViewer";
    private static final int NORMAL_PADDING = 14;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final int MAX_REDIRECTS = 6;
    private static final String RELAXED_TOKEN = "gofile";
    private static final String ALLOWED_RULE = "HTTPS かつ URL文字列に \"gofile\" を含むURL";
    private static final String RESOURCE_RULE = "追加リソース許可: *.fun800.click（サムネ/動画検証用）";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout mainLayout;
    private LinearLayout topPanel;
    private EditText urlInput;
    private TextView statusView;
    private WebView webView;
    private Button jsButton;
    private Button maxButton;

    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private int normalSystemUiVisibility;

    // v0.6: WebViewのサイト側fullscreenに頼らず、アプリ側の縦向き最大表示モードを追加する。
    // 強制的な横画面化はしない。
    private boolean limitedJavaScriptEnabled = true;
    private boolean viewerMaximized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();

        String initialUrl = urlFromIntent();
        if (initialUrl != null) {
            urlInput.setText(initialUrl);
            openRequestedUrl(initialUrl);
        } else {
            setStatus("URLを貼り付けるか、Xなどの共有から開いてください。制限付きJSは初期ONです。許可条件: " + ALLOWED_RULE + " / " + RESOURCE_RULE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(limitedJavaScriptEnabled);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setDomStorageEnabled(limitedJavaScriptEnabled);
        settings.setDatabaseEnabled(limitedJavaScriptEnabled);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new SafeWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                appendLog("BLOCK popup/window.open");
                return false;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    appendLog("PROGRESS 100%");
                    schedulePageCleanup();
                    if (viewerMaximized) {
                        injectPlayerMaxCss(true);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && title.trim().length() > 0) {
                    appendLog("TITLE " + title);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullScreenView(view, callback);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                // サイト側が横画面を要求しても、このビューアでは画面方向を強制しない。
                showFullScreenView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullScreenView();
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                appendLog("BLOCK download: " + url + " / " + mimetype + " / " + contentLength + " bytes");
                setStatus("自動ダウンロードを遮断しました。v0.6では保存機能は入れていません。");
            }
        });
    }

    private void buildUi() {
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(NORMAL_PADDING, NORMAL_PADDING, NORMAL_PADDING, NORMAL_PADDING);

        topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(topPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Gofile Safe Viewer");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("URL内に gofile を含むHTTPS URL");
        controls.addView(urlInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button openButton = new Button(this);
        openButton.setText("開く");
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRequestedUrl(urlInput.getText().toString());
            }
        });
        controls.addView(openButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topPanel.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        jsButton = new Button(this);
        jsButton.setText(limitedJavaScriptEnabled ? "制限付きJS: ON" : "制限付きJS: OFF");
        jsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLimitedJavaScript();
            }
        });
        actions.addView(jsButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        maxButton = new Button(this);
        maxButton.setText("画面最大");
        maxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterViewerMaxMode();
            }
        });
        actions.addView(maxButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        topPanel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        topPanel.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        mainLayout.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(mainLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void toggleLimitedJavaScript() {
        limitedJavaScriptEnabled = !limitedJavaScriptEnabled;
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(limitedJavaScriptEnabled);
        settings.setDomStorageEnabled(limitedJavaScriptEnabled);
        settings.setDatabaseEnabled(limitedJavaScriptEnabled);
        jsButton.setText(limitedJavaScriptEnabled ? "制限付きJS: ON" : "制限付きJS: OFF");
        appendLog("INFO limited JavaScript = " + limitedJavaScriptEnabled);
        setStatus(limitedJavaScriptEnabled
                ? "JavaScriptを有効化しました。許可条件外通信、ポップアップ、自動DLは遮断します。"
                : "JavaScriptを無効化しました。");

        String currentUrl = webView.getUrl();
        if (currentUrl != null && isAllowedUrl(Uri.parse(currentUrl))) {
            appendLog("RELOAD after JS toggle: " + currentUrl);
            webView.reload();
        }
    }

    private String urlFromIntent() {
        if (getIntent() == null) {
            return null;
        }
        String action = getIntent().getAction();
        if (android.content.Intent.ACTION_VIEW.equals(action) && getIntent().getData() != null) {
            return getIntent().getData().toString();
        }
        if (android.content.Intent.ACTION_SEND.equals(action)) {
            CharSequence shared = getIntent().getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
            if (shared != null) {
                return extractBestUrl(shared.toString());
            }
        }
        return null;
    }

    private String extractBestUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text == null ? "" : text);
        String first = null;
        while (matcher.find()) {
            String url = stripTrailingPunctuation(matcher.group());
            if (first == null) {
                first = url;
            }
            Uri uri = Uri.parse(url);
            if (isAllowedUrl(uri)) {
                return url;
            }
        }
        return first;
    }

    private String stripTrailingPunctuation(String url) {
        String result = url.trim();
        while (result.endsWith(")") || result.endsWith("]") || result.endsWith("}")
                || result.endsWith("、") || result.endsWith("。") || result.endsWith(",")
                || result.endsWith(".") || result.endsWith("\"") || result.endsWith("'")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void openRequestedUrl(String rawInput) {
        String extracted = extractBestUrl(rawInput);
        if (extracted == null || extracted.trim().isEmpty()) {
            setStatus("URLが見つかりません。");
            return;
        }

        Uri uri = Uri.parse(extracted);
        if (isAllowedUrl(uri)) {
            loadSafe(extracted);
            return;
        }

        if (isSupportedRedirectResolverHost(uri.getHost()) && "https".equalsIgnoreCase(uri.getScheme())) {
            setStatus("短縮URLをWebViewではなくヘッダー取得だけで展開しています: " + extracted);
            resolveThenOpen(extracted);
            return;
        }

        appendLog("BLOCK input: " + extracted);
        setStatus("許可対象外URLです。" + ALLOWED_RULE + " のみ開きます: " + extracted);
    }

    private void resolveThenOpen(final String url) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ResolveResult result = resolveRedirects(url);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.error != null) {
                            appendLog("BLOCK resolve error: " + result.error);
                            setStatus("短縮URLの展開に失敗しました: " + result.error);
                            return;
                        }
                        appendLog("INFO resolved: " + url + " -> " + result.finalUrl);
                        Uri finalUri = Uri.parse(result.finalUrl);
                        if (isAllowedUrl(finalUri)) {
                            urlInput.setText(result.finalUrl);
                            loadSafe(result.finalUrl);
                        } else {
                            appendLog("BLOCK resolved target: " + result.finalUrl);
                            setStatus("展開後URLが許可対象外です: " + result.finalUrl);
                        }
                    }
                });
            }
        }).start();
    }

    private ResolveResult resolveRedirects(String startUrl) {
        String current = startUrl;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(current);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 GofileSafeViewer/0.6");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        return ResolveResult.error("redirect without Location");
                    }
                    URL nextUrl = new URL(url, location);
                    current = nextUrl.toString();
                    Uri nextUri = Uri.parse(current);
                    if (!"https".equalsIgnoreCase(nextUri.getScheme())) {
                        return ResolveResult.error("non-HTTPS redirect blocked: " + current);
                    }
                    String host = nextUri.getHost();
                    if (isAllowedUrl(nextUri) || isSupportedRedirectResolverHost(host)) {
                        continue;
                    }
                    return ResolveResult.ok(current);
                }
                return ResolveResult.ok(current);
            } catch (IOException ex) {
                return ResolveResult.error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return ResolveResult.error("too many redirects");
    }

    private void loadSafe(String url) {
        appendLog("LOAD " + url);
        setStatus("読み込み中: " + url + " / 許可条件: " + ALLOWED_RULE + " / " + RESOURCE_RULE + " / JS: " + (limitedJavaScriptEnabled ? "ON" : "OFF"));
        webView.loadUrl(url);
    }

    private boolean isAllowedUrl(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return containsRelaxedToken(uri.toString());
    }

    private boolean isAllowedResourceUrl(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if ("data".equalsIgnoreCase(scheme)) {
            return true;
        }
        if ("blob".equalsIgnoreCase(scheme)) {
            return containsRelaxedToken(uri.toString());
        }
        if (!"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (containsRelaxedToken(uri.toString())) {
            return true;
        }
        return isTemporaryMediaResourceHost(uri.getHost());
    }

    private boolean isTemporaryMediaResourceHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("fun800.click")
                || h.endsWith(".fun800.click");
    }

    private boolean containsRelaxedToken(String value) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(RELAXED_TOKEN);
    }

    private boolean isSupportedRedirectResolverHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("t.co") || h.endsWith(".t.co");
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private void setStatus(final String message) {
        statusView.setText(message);
    }

    private void appendLog(final String line) {
        Log.d(TAG, line);
    }

    private void schedulePageCleanup() {
        injectPageCleanup();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                injectPageCleanup();
            }
        }, 300);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                injectPageCleanup();
            }
        }, 1200);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                injectPageCleanup();
            }
        }, 3000);
    }

    private void injectPageCleanup() {
        if (webView == null) {
            return;
        }
        String script = "(function(){"
                + "if(window.__gsvCleanupInstalled){if(window.__gsvCleanupRun)window.__gsvCleanupRun();return;}"
                + "window.__gsvCleanupInstalled=true;"
                + "var css='.video-float-ad,a[href*=\\\"kinv.cc\\\"],[class*=\\\"float-ad\\\"],[class*=\\\"Pulse\\\"],[class*=\\\"pulse\\\"],[id*=\\\"Pulse\\\"],[id*=\\\"pulse\\\"]{display:none!important;visibility:hidden!important;pointer-events:none!important;}';"
                + "var style=document.getElementById('gsv-cleanup-style');"
                + "if(!style){style=document.createElement('style');style.id='gsv-cleanup-style';style.textContent=css;document.documentElement.appendChild(style);}"
                + "function clean(){"
                + "var selector='.video-float-ad,a[href*=\\\"kinv.cc\\\"],[class*=\\\"float-ad\\\"],a[href*=\\\"pulse\\\"],a[href*=\\\"Pulse\\\"],[class*=\\\"pulse\\\"],[class*=\\\"Pulse\\\"],[id*=\\\"pulse\\\"],[id*=\\\"Pulse\\\"]';"
                + "document.querySelectorAll(selector).forEach(function(el){el.remove();});"
                + "document.querySelectorAll('a,button').forEach(function(el){var t=(el.textContent||'').trim();if(t.indexOf('PulsePlayer')>=0||t.indexOf('Pulse Player')>=0||t.indexOf('Kinv')>=0){el.remove();}});"
                + "}"
                + "window.__gsvCleanupRun=clean;"
                + "clean();"
                + "try{new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void enterViewerMaxMode() {
        if (viewerMaximized) {
            return;
        }
        appendLog("ENTER viewer max mode");
        viewerMaximized = true;
        normalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();

        if (topPanel != null) {
            topPanel.setVisibility(View.GONE);
        }
        if (mainLayout != null) {
            mainLayout.setPadding(0, 0, 0, 0);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        injectPlayerMaxCss(true);
    }

    private void exitViewerMaxMode() {
        if (!viewerMaximized) {
            return;
        }
        appendLog("EXIT viewer max mode");
        viewerMaximized = false;
        injectPlayerMaxCss(false);

        if (topPanel != null) {
            topPanel.setVisibility(View.VISIBLE);
        }
        if (mainLayout != null) {
            mainLayout.setPadding(NORMAL_PADDING, NORMAL_PADDING, NORMAL_PADDING, NORMAL_PADDING);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(normalSystemUiVisibility);
        schedulePageCleanup();
    }

    private void injectPlayerMaxCss(boolean enabled) {
        if (webView == null) {
            return;
        }
        String script;
        if (enabled) {
            script = "(function(){"
                    + "var id='gsv-max-style';"
                    + "var css='html,body,#app,.app-page{margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;overflow:hidden!important;background:#000!important;}'"
                    + "+'.ad-banner,.content-list,.player-title,.video-float-ad,[class*=\\\"float-ad\\\"]{display:none!important;visibility:hidden!important;pointer-events:none!important;}'"
                    + "+'.landing-content,.player-view{position:fixed!important;inset:0!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;background:#000!important;z-index:2147483646!important;display:flex!important;align-items:center!important;justify-content:center!important;overflow:hidden!important;}'"
                    + "+'.player-frame{position:fixed!important;inset:0!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;background:#000!important;z-index:2147483647!important;display:flex!important;align-items:center!important;justify-content:center!important;overflow:hidden!important;}'"
                    + "+'.player-frame video,.player-frame canvas,.player-frame img,.player-cover,video,canvas{width:100vw!important;height:100vh!important;max-width:100vw!important;max-height:100vh!important;object-fit:contain!important;background:#000!important;}'"
                    + "+'.player-start{position:absolute!important;left:50%!important;top:50%!important;transform:translate(-50%,-50%)!important;z-index:2147483647!important;}';"
                    + "var style=document.getElementById(id);"
                    + "if(!style){style=document.createElement('style');style.id=id;document.documentElement.appendChild(style);}"
                    + "style.textContent=css;"
                    + "if(window.__gsvCleanupRun)window.__gsvCleanupRun();"
                    + "})();";
        } else {
            script = "(function(){var style=document.getElementById('gsv-max-style');if(style)style.remove();if(window.__gsvCleanupRun)window.__gsvCleanupRun();})();";
        }
        webView.evaluateJavascript(script, null);
    }

    private void showFullScreenView(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullScreenView != null) {
            if (callback != null) {
                callback.onCustomViewHidden();
            }
            return;
        }
        appendLog("SHOW fullscreen custom view without orientation change");
        normalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        fullScreenView = view;
        fullScreenCallback = callback;
        fullScreenView.setBackgroundColor(Color.BLACK);

        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        decor.addView(fullScreenView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void hideFullScreenView() {
        if (fullScreenView == null) {
            return;
        }
        appendLog("HIDE fullscreen custom view");
        ViewGroup parent = (ViewGroup) fullScreenView.getParent();
        if (parent != null) {
            parent.removeView(fullScreenView);
        }
        fullScreenView = null;
        if (mainLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(normalSystemUiVisibility);
        WebChromeClient.CustomViewCallback callback = fullScreenCallback;
        fullScreenCallback = null;
        if (callback != null) {
            callback.onCustomViewHidden();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullScreenView != null) {
            hideFullScreenView();
            return;
        }
        if (viewerMaximized) {
            exitViewerMaxMode();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (fullScreenView != null) {
                hideFullScreenView();
            }
            if (viewerMaximized) {
                exitViewerMaxMode();
            }
            if (webView != null) {
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
            }
            WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(null);
        } catch (Exception ignored) {
            // Best effort cleanup.
        }
        super.onDestroy();
    }

    private final class SafeWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldBlockTopNavigation(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return shouldBlockTopNavigation(Uri.parse(url));
        }

        private boolean shouldBlockTopNavigation(Uri uri) {
            if (isAllowedUrl(uri)) {
                return false;
            }
            appendLog("BLOCK navigation: " + (uri == null ? "null" : uri.toString()));
            setStatus("外部遷移を遮断しました: " + (uri == null ? "null" : uri.toString()));
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isAllowedResourceUrl(uri)) {
                if (uri != null && isTemporaryMediaResourceHost(uri.getHost())) {
                    appendLog("ALLOW media/thumb: " + uri);
                }
                return null;
            }
            appendLog("BLOCK resource: " + uri);
            return blockedResponse();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            appendLog("DONE " + url);
            schedulePageCleanup();
            if (viewerMaximized) {
                injectPlayerMaxCss(true);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null) {
                appendLog("ERROR resource: " + request.getUrl() + " / " + error.getDescription());
                if (request.isForMainFrame()) {
                    setStatus("読み込みエラー: " + error.getDescription());
                }
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            appendLog("BLOCK SSL error: " + error);
            setStatus("SSLエラーのため読み込みを中止しました。");
        }
    }

    private static final class ResolveResult {
        final String finalUrl;
        final String error;

        private ResolveResult(String finalUrl, String error) {
            this.finalUrl = finalUrl;
            this.error = error;
        }

        static ResolveResult ok(String finalUrl) {
            return new ResolveResult(finalUrl, null);
        }

        static ResolveResult error(String error) {
            return new ResolveResult(null, error);
        }
    }
}
