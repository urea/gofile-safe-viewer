package jp.urea.gofilesafeviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
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
    private static final int MAX_REDIRECTS = 6;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final String ALLOWED_RULE = "HTTPSのgofile/twimgを含むURL、x.com / *.x.com / t.co";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private EditText urlInput;
    private TextView statusView;
    private WebView webView;
    private int normalSystemUiVisibility;
    private View customFullScreenView;
    private WebChromeClient.CustomViewCallback customFullScreenCallback;
    private volatile boolean destroyed;
    private int navigationGeneration;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); buildUi(); configureWebView();
        String initialUrl = urlFromIntent();
        if (saved != null && saved.getString("url") != null) initialUrl = saved.getString("url");
        if (initialUrl != null) { urlInput.setText(initialUrl); openRequestedUrl(initialUrl); }
        else setStatus("URLを貼り付けてください。許可条件: " + ALLOWED_RULE);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (webView.getUrl() != null && isAllowedUrl(Uri.parse(webView.getUrl()))) out.putString("url", webView.getUrl());
        super.onSaveInstanceState(out);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new SafeClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message result) { return false; }
            @Override public void onProgressChanged(WebView view, int progress) { if (progress == 100) cleanupPage(); }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) { showCustomFullScreen(view, callback); }
            @Override public void onShowCustomView(View view, int orientation, CustomViewCallback callback) { showCustomFullScreen(view, callback); }
            @Override public void onHideCustomView() { hideCustomFullScreen(); }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mime, size) -> setStatus("ページからのダウンロードを遮断しました。アプリの更新はホームから行ってください。"));
        // Never add a JavaScript interface: web pages must not access native update controls.
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true);
        int padding = Math.round(10 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        LinearLayout heading = new LinearLayout(this);
        TextView title = new TextView(this); title.setText("Gofile Safe Viewer"); title.setTextSize(18);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = new Button(this); home.setText("ホーム"); home.setOnClickListener(v -> goHome());
        heading.addView(home, new LinearLayout.LayoutParams(-2, -2)); root.addView(heading);
        LinearLayout controls = new LinearLayout(this);
        urlInput = new EditText(this); urlInput.setSingleLine(true); urlInput.setTextSize(14);
        urlInput.setHint("gofile / twimg / x.com / t.co のHTTPS URL");
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        controls.addView(urlInput, new LinearLayout.LayoutParams(0, -2, 1));
        Button open = new Button(this); open.setText("開く"); open.setOnClickListener(v -> openRequestedUrl(urlInput.getText().toString()));
        controls.addView(open, new LinearLayout.LayoutParams(-2, -2)); root.addView(controls);
        statusView = new TextView(this); statusView.setTextSize(12); statusView.setMaxLines(2); statusView.setEllipsize(TextUtils.TruncateAt.END); root.addView(statusView);
        webView = new WebView(this); root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
    private void goHome() {
        hideCustomFullScreen();
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private String urlFromIntent() {
        Intent intent = getIntent(); if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) return intent.getData().toString();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) return extractBestUrl(text.toString());
        }
        return null;
    }
    private String extractBestUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text == null ? "" : text); String first = null;
        while (matcher.find()) {
            String url = stripTrailingPunctuation(matcher.group()); if (first == null) first = url;
            Uri uri = Uri.parse(url);
            if (isAllowedUrl(uri) || isResolverHost(uri.getHost())) return url;
        }
        return first;
    }
    private String stripTrailingPunctuation(String url) {
        String result = url.trim();
        while (!result.isEmpty() && ")]},.、。\"'".indexOf(result.charAt(result.length() - 1)) >= 0) result = result.substring(0, result.length() - 1);
        return result;
    }
    private void openRequestedUrl(String raw) {
        navigationGeneration++;
        String url = extractBestUrl(raw);
        if (url == null || url.trim().isEmpty()) { setStatus("URLが見つかりません。"); return; }
        Uri uri = Uri.parse(url);
        if ("https".equalsIgnoreCase(uri.getScheme()) && isResolverHost(uri.getHost())) {
            setStatus("短縮URLを展開しています…"); resolveThenOpen(url); return;
        }
        if (isAllowedUrl(uri)) { loadSafe(url); return; }
        setStatus("許可対象外URLです。" + ALLOWED_RULE + " のみ開きます。");
    }
    private void resolveThenOpen(String startUrl) {
        final int generation = navigationGeneration;
        new Thread(() -> {
            ResolveResult result = resolveRedirects(startUrl);
            mainHandler.post(() -> {
                if (destroyed || generation != navigationGeneration) return;
                if (result.error != null) { setStatus("短縮URLの展開に失敗しました: " + result.error); return; }
                if (isAllowedUrl(Uri.parse(result.finalUrl))) { urlInput.setText(result.finalUrl); loadSafe(result.finalUrl); }
                else setStatus("展開後URLが許可対象外です。");
            });
        }, "safe-url-resolver").start();
    }
    private ResolveResult resolveRedirects(String startUrl) {
        String current = startUrl;
        for (int i = 0; i < MAX_REDIRECTS && !destroyed; i++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(current); connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false); connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 GofileSafeViewer/" + BuildConfig.VERSION_NAME);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) return ResolveResult.error("転送情報がありません。");
                    current = new URL(url, location).toString(); Uri next = Uri.parse(current);
                    if (!"https".equalsIgnoreCase(next.getScheme())) return ResolveResult.error("HTTPS以外への転送を遮断しました。");
                    if (isAllowedUrl(next) || isResolverHost(next.getHost())) continue;
                }
                return ResolveResult.ok(current);
            } catch (IOException ex) { return ResolveResult.error("通信を完了できませんでした。"); }
            finally { if (connection != null) connection.disconnect(); }
        }
        return ResolveResult.error("転送回数の上限または画面終了により中止しました。");
    }
    private void loadSafe(String url) { setStatus("読み込み中: " + url); webView.loadUrl(url); }
    private boolean isAllowedUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && (containsAllowedToken(uri.toString()) || isAllowedNamedHostUrl(uri.toString()));
    }
    private boolean isAllowedResourceUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if ("data".equalsIgnoreCase(scheme)) return true;
        if ("blob".equalsIgnoreCase(scheme)) return containsAllowedToken(uri.toString()) || isAllowedNamedHostUrl(uri.getSchemeSpecificPart());
        return "https".equalsIgnoreCase(scheme) && (isAllowedUrl(uri) || isTemporaryMediaHost(uri.getHost()));
    }
    private boolean containsAllowedToken(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT); return lower.contains("gofile") || lower.contains("twimg");
    }
    private boolean isAllowedNamedHostUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return host.equals("x.com") || host.endsWith(".x.com") || host.equals("t.co");
        } catch (URISyntaxException ex) { return false; }
    }
    private boolean isTemporaryMediaHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT); return lower.equals("fun800.click") || lower.endsWith(".fun800.click");
    }
    private boolean isResolverHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT); return lower.equals("t.co") || lower.endsWith(".t.co");
    }
    private void cleanupPage() {
        if (destroyed) return;
        injectCleanupScript();
        mainHandler.postDelayed(this::injectCleanupScript, 300);
        mainHandler.postDelayed(this::injectCleanupScript, 1200);
        mainHandler.postDelayed(this::injectCleanupScript, 3000);
    }
    private void injectCleanupScript() {
        if (destroyed) return;
        String script = "(function(){"
                + "var css='.video-float-ad,[class*=\"float-ad\"],[id*=\"float-ad\"]{display:none!important;visibility:hidden!important;pointer-events:none!important;}';"
                + "var s=document.getElementById('gsv-cleanup-style');"
                + "if(!s){s=document.createElement('style');s.id='gsv-cleanup-style';document.documentElement.appendChild(s);}s.textContent=css;"
                + "function clean(){document.querySelectorAll('.video-float-ad,[class*=\"float-ad\"],[id*=\"float-ad\"]').forEach(function(e){e.remove();});}clean();"
                + "if(!window.__gsvCleanupObserver){try{window.__gsvCleanupObserver=new MutationObserver(clean);window.__gsvCleanupObserver.observe(document.documentElement,{childList:true,subtree:true});}catch(e){}}})();";
        webView.evaluateJavascript(script, null);
    }
    private void showCustomFullScreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customFullScreenView != null) { if (callback != null) callback.onCustomViewHidden(); return; }
        normalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        customFullScreenView = view; customFullScreenCallback = callback; view.setBackgroundColor(Color.BLACK);
        root.setVisibility(View.GONE);
        ((ViewGroup) getWindow().getDecorView()).addView(view, new ViewGroup.LayoutParams(-1, -1));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void hideCustomFullScreen() {
        if (customFullScreenView == null) return;
        ViewGroup parent = (ViewGroup) customFullScreenView.getParent(); if (parent != null) parent.removeView(customFullScreenView);
        customFullScreenView = null; root.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(normalSystemUiVisibility);
        WebChromeClient.CustomViewCallback callback = customFullScreenCallback; customFullScreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }
    private void setStatus(String message) { if (!destroyed) statusView.setText(message); }
    @Override public void onBackPressed() {
        if (customFullScreenView != null) { hideCustomFullScreen(); return; }
        if (webView.canGoBack()) { webView.goBack(); return; }
        goHome();
    }
    @Override protected void onPause() { CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true; mainHandler.removeCallbacksAndMessages(null); hideCustomFullScreen();
        if (webView != null) {
            webView.stopLoading(); root.removeView(webView); webView.destroy();
        }
        // Persist cookies and WebStorage. Deletion is an explicit native home-screen action.
        CookieManager.getInstance().flush(); super.onDestroy();
    }
    private final class SafeClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return block(request.getUrl()); }
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return block(Uri.parse(url)); }
        private boolean block(Uri uri) {
            if (isAllowedUrl(uri)) return false;
            setStatus("許可対象外への遷移を遮断しました。"); return true;
        }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (isAllowedResourceUrl(request.getUrl())) return null;
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        @Override public void onPageFinished(WebView view, String url) {
            cleanupPage(); CookieManager.getInstance().flush();
            if (isAllowedUrl(Uri.parse(url))) { urlInput.setText(url); setStatus("表示中: " + url); }
        }
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) setStatus("ページを読み込めませんでした。通信状態や許可条件を確認してください。");
        }
        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); setStatus("証明書エラーのため読み込みを中止しました。");
        }
    }
    private static final class ResolveResult {
        final String finalUrl, error;
        private ResolveResult(String url, String error) { this.finalUrl = url; this.error = error; }
        static ResolveResult ok(String url) { return new ResolveResult(url, null); }
        static ResolveResult error(String error) { return new ResolveResult(null, error); }
    }
}
