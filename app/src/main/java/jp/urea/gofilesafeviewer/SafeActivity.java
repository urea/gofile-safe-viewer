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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafeActivity extends Activity {
    private static final String TAG = "GofileSafeViewer";
    private static final int PADDING = 14;
    private static final int MAX_REDIRECTS = 6;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final String ALLOWED_RULE = "HTTPS かつ gofile/twimg を含むURL、または x.com / *.x.com / t.co";
    private static final String RESOURCE_RULE = "追加リソース許可: *.fun800.click";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private EditText urlInput;
    private TextView statusView;
    private WebView webView;

    private int normalSystemUiVisibility;
    private View customFullScreenView;
    private WebChromeClient.CustomViewCallback customFullScreenCallback;

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
            setStatus("URLを貼るか、Xなどの共有から開いてください。許可条件: " + ALLOWED_RULE + " / " + RESOURCE_RULE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
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
        webView.setWebViewClient(new SafeClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                log("BLOCK popup/window.open");
                return false;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress == 100) {
                    cleanupPage();
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && title.trim().length() > 0) {
                    log("TITLE " + title);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showCustomFullScreen(view, callback);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                showCustomFullScreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideCustomFullScreen();
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                log("BLOCK download: " + url);
                setStatus("自動ダウンロードを遮断しました。");
            }
        });
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(PADDING, PADDING, PADDING, PADDING);

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(topPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Gofile Safe Viewer");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("gofile / twimg / x.com / t.co のHTTPS URL");
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

        statusView = new TextView(this);
        statusView.setTextSize(14);
        topPanel.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
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
            CharSequence text = getIntent().getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
            if (text != null) {
                return extractBestUrl(text.toString());
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
            if (isAllowedUrl(uri) || isResolverHost(uri.getHost())) {
                return url;
            }
        }
        return first;
    }

    private String stripTrailingPunctuation(String url) {
        String result = url.trim();
        while (result.endsWith(")") || result.endsWith("]") || result.endsWith("}") ||
                result.endsWith("、") || result.endsWith("。") || result.endsWith(",") ||
                result.endsWith(".") || result.endsWith("\"") || result.endsWith("'")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void openRequestedUrl(String rawInput) {
        String url = extractBestUrl(rawInput);
        if (url == null || url.trim().isEmpty()) {
            setStatus("URLが見つかりません。");
            return;
        }

        Uri uri = Uri.parse(url);
        if ("https".equalsIgnoreCase(uri.getScheme()) && isResolverHost(uri.getHost())) {
            setStatus("短縮URLを展開しています: " + url);
            resolveThenOpen(url);
            return;
        }

        if (isAllowedUrl(uri)) {
            loadSafe(url);
            return;
        }

        setStatus("許可対象外URLです。" + ALLOWED_RULE + " のみ開きます: " + url);
        log("BLOCK input: " + url);
    }

    private void resolveThenOpen(final String startUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ResolveResult result = resolveRedirects(startUrl);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.error != null) {
                            setStatus("短縮URLの展開に失敗しました: " + result.error);
                            return;
                        }
                        Uri uri = Uri.parse(result.finalUrl);
                        if (isAllowedUrl(uri)) {
                            urlInput.setText(result.finalUrl);
                            loadSafe(result.finalUrl);
                        } else {
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
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 GofileSafeViewer/0.9");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        return ResolveResult.error("redirect without Location");
                    }
                    current = new URL(url, location).toString();
                    Uri nextUri = Uri.parse(current);
                    if (!"https".equalsIgnoreCase(nextUri.getScheme())) {
                        return ResolveResult.error("non-HTTPS redirect blocked");
                    }
                    if (isAllowedUrl(nextUri) || isResolverHost(nextUri.getHost())) {
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
        setStatus("読み込み中: " + url + " / 許可条件: " + ALLOWED_RULE + " / " + RESOURCE_RULE);
        log("LOAD " + url);
        webView.loadUrl(url);
    }

    private boolean isAllowedUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        return containsAllowedToken(uri.toString()) || isAllowedNamedHostUrl(uri.toString());
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
            return containsAllowedToken(uri.toString()) || isAllowedNamedHostUrl(uri.getSchemeSpecificPart());
        }
        if (!"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return isAllowedUrl(uri) || isTemporaryMediaHost(uri.getHost());
    }

    private boolean containsAllowedToken(String value) {
        if (value == null) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("gofile") || v.contains("twimg");
    }

    private boolean isAllowedNamedHostUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            // Parse the authority strictly; text in a path/query must not grant access.
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return h.equals("x.com") || h.endsWith(".x.com") || h.equals("t.co");
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean isTemporaryMediaHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("fun800.click") || h.endsWith(".fun800.click");
    }

    private boolean isResolverHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("t.co") || h.endsWith(".t.co");
    }

    private void cleanupPage() {
        injectCleanupScript();
        runLater(300, new Runnable() { @Override public void run() { injectCleanupScript(); } });
        runLater(1200, new Runnable() { @Override public void run() { injectCleanupScript(); } });
        runLater(3000, new Runnable() { @Override public void run() { injectCleanupScript(); } });
    }

    private void injectCleanupScript() {
        String script = "(function(){"
                + "var css='.video-float-ad,[class*=\\\"float-ad\\\"],[id*=\\\"float-ad\\\"]{display:none!important;visibility:hidden!important;pointer-events:none!important;}';"
                + "var s=document.getElementById('gsv-cleanup-style');"
                + "if(!s){s=document.createElement('style');s.id='gsv-cleanup-style';document.documentElement.appendChild(s);}"
                + "s.textContent=css;"
                + "function clean(){document.querySelectorAll('.video-float-ad,[class*=\\\"float-ad\\\"],[id*=\\\"float-ad\\\"]').forEach(function(e){e.remove();});}"
                + "clean();"
                + "if(!window.__gsvCleanupObserver){try{window.__gsvCleanupObserver=new MutationObserver(clean);window.__gsvCleanupObserver.observe(document.documentElement,{childList:true,subtree:true});}catch(e){}}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void showCustomFullScreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customFullScreenView != null) {
            if (callback != null) {
                callback.onCustomViewHidden();
            }
            return;
        }
        normalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        customFullScreenView = view;
        customFullScreenCallback = callback;
        view.setBackgroundColor(Color.BLACK);
        root.setVisibility(View.GONE);
        ((ViewGroup) getWindow().getDecorView()).addView(view,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void hideCustomFullScreen() {
        if (customFullScreenView == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) customFullScreenView.getParent();
        if (parent != null) {
            parent.removeView(customFullScreenView);
        }
        customFullScreenView = null;
        root.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(normalSystemUiVisibility);
        WebChromeClient.CustomViewCallback callback = customFullScreenCallback;
        customFullScreenCallback = null;
        if (callback != null) {
            callback.onCustomViewHidden();
        }
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private void runLater(int delayMillis, Runnable runnable) {
        mainHandler.postDelayed(runnable, delayMillis);
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    @Override
    public void onBackPressed() {
        if (customFullScreenView != null) {
            hideCustomFullScreen();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
            }
            WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(null);
        } catch (Exception ignored) {
            // best effort cleanup
        }
        super.onDestroy();
    }

    private final class SafeClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return blockNavigationIfNeeded(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return blockNavigationIfNeeded(Uri.parse(url));
        }

        private boolean blockNavigationIfNeeded(Uri uri) {
            if (isAllowedUrl(uri)) {
                return false;
            }
            setStatus("外部遷移を遮断しました: " + (uri == null ? "null" : uri.toString()));
            log("BLOCK navigation: " + uri);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isAllowedResourceUrl(uri)) {
                return null;
            }
            log("BLOCK resource: " + uri);
            return emptyResponse();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            cleanupPage();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                setStatus("読み込みエラー: " + error.getDescription());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
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
