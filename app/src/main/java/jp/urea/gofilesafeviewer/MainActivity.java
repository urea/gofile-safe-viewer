package jp.urea.gofilesafeviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final int MAX_REDIRECTS = 6;
    private static final String RELAXED_TOKEN = "gofile";
    private static final String ALLOWED_RULE = "HTTPS かつ URL文字列に \"gofile\" を含むURL";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText urlInput;
    private TextView statusView;
    private TextView logView;
    private WebView webView;
    private Button jsButton;

    // v0.3: Gofile系ページはSPA/JS前提の可能性が高いため、表示検証を優先して初期ONにする。
    // 外部遷移、ポップアップ、自動ダウンロード、許可条件外リソース遮断は継続する。
    private boolean limitedJavaScriptEnabled = true;

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
            setStatus("URLを貼り付けるか、Xなどの共有から開いてください。表示優先のため制限付きJSは初期ONです。許可条件: " + ALLOWED_RULE);
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

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
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && title.trim().length() > 0) {
                    appendLog("TITLE " + title);
                }
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                appendLog("BLOCK download: " + url + " / " + mimetype + " / " + contentLength + " bytes");
                setStatus("自動ダウンロードを遮断しました。v0.3では保存機能は入れていません。");
            }
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14, 14, 14, 14);

        TextView title = new TextView(this);
        title.setText("Gofile Safe Viewer");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        root.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        Button clearButton = new Button(this);
        clearButton.setText("ログ消去");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("");
            }
        });
        actions.addView(clearButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        root.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        logView = new TextView(this);
        logView.setTextSize(12);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
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
                ? "JavaScriptを有効化しました。URL内に gofile を含まない通信、ポップアップ、自動DLは遮断します。"
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
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 GofileSafeViewer/0.3");
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
        setStatus("読み込み中: " + url + " / 許可条件: " + ALLOWED_RULE + " / JS: " + (limitedJavaScriptEnabled ? "ON" : "OFF"));
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

    private boolean isAllowedHost(String host) {
        return containsRelaxedToken(host);
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
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String current = logView.getText().toString();
                logView.setText(current + line + "\n");
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
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
            if (isAllowedUrl(uri)) {
                return null;
            }
            appendLog("BLOCK resource: " + uri);
            return blockedResponse();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            appendLog("DONE " + url);
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
