package jp.urea.gofilesafeviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.Toast;
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
    private static final String ALLOWED_RULE = "HTTPSのgofile/twimg/mvfileを含むURL、x.com / *.x.com / t.co";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private EditText urlInput;
    private TextView statusView;
    private Button bookmarkButton;
    private WebView webView;
    private View customFullScreenView;
    private WebChromeClient.CustomViewCallback customFullScreenCallback;
    private volatile boolean destroyed;
    private int navigationGeneration;
    private boolean pageHadIssue;

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
    @Override protected void onResume() {
        super.onResume();
        if (customFullScreenView == null) UiChrome.showSystemBars(this);
        updateBookmarkButton();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && customFullScreenView == null) UiChrome.showSystemBars(this);
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
        webView.setDownloadListener((url, userAgent, contentDisposition, mime, size) -> {
            pageHadIssue = true;
            setStatus("ページからのダウンロードを遮断しました。アプリの更新はホームから行ってください。");
        });
        // Never add a JavaScript interface: web pages must not access native update controls.
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFocusableInTouchMode(true);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setText("Gofile Safe Viewer"); title.setTextSize(16);
        title.setTextColor(Color.rgb(35, 43, 51)); title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        bookmarkButton = toolbarButton("☆保存", false, this::toggleBookmark);
        bookmarkButton.setEnabled(false);
        heading.addView(bookmarkButton, new LinearLayout.LayoutParams(dp(72), dp(48)));
        Button home = toolbarButton("ホーム", false, this::goHome);
        heading.addView(home, new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), 0, dp(8), dp(4));
        urlInput = new EditText(this);
        urlInput.setSingleLine(true); urlInput.setTextSize(14);
        urlInput.setTextColor(Color.rgb(35, 43, 51));
        urlInput.setHint("HTTPSのURLを入力");
        urlInput.setHintTextColor(Color.rgb(95, 105, 115));
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setBackground(new InsetDrawable(rounded(Color.rgb(243, 245, 247)), 0, dp(4), 0, dp(4)));
        urlInput.setPadding(dp(12), 0, dp(12), 0);
        urlInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { submitUrl(); return true; }
            return false;
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        inputParams.setMarginEnd(dp(8));
        controls.addView(urlInput, inputParams);
        controls.addView(toolbarButton("開く", true, this::submitUrl), new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setTextSize(12); statusView.setTextColor(Color.rgb(82, 93, 103));
        statusView.setMaxLines(2); statusView.setEllipsize(TextUtils.TruncateAt.END);
        statusView.setPadding(dp(14), 0, dp(14), dp(4));
        statusView.setVisibility(View.GONE); root.addView(statusView);
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(230, 233, 236));
        root.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        UiChrome.setContentView(this, root);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(8)); return drawable;
    }
    private Button toolbarButton(String label, boolean filled, Runnable action) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(label); button.setTextSize(13); button.setAllCaps(false);
        button.setTextColor(Color.rgb(35, 43, 51)); button.setIncludeFontPadding(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0); button.setStateListAnimator(null);
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x1f000000),
                rounded(filled ? Color.rgb(235, 239, 242) : Color.TRANSPARENT), rounded(Color.WHITE));
        button.setBackground(new InsetDrawable(ripple, 0, dp(4), 0, dp(4)));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void submitUrl() {
        String value = urlInput.getText().toString();
        urlInput.clearFocus(); root.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        openRequestedUrl(value);
    }
    private void goHome() {
        hideCustomFullScreen();
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private void toggleBookmark() {
        String url = currentBookmarkUrl();
        if (url == null) return;
        BookmarkStore bookmarks = new BookmarkStore(this);
        if (bookmarks.contains(url)) {
            bookmarks.remove(url);
            Toast.makeText(this, "ブックマークを削除しました。", Toast.LENGTH_SHORT).show();
        } else {
            bookmarks.add(url);
            Toast.makeText(this, "ブックマークに保存しました。", Toast.LENGTH_SHORT).show();
        }
        updateBookmarkButton();
    }
    private String currentBookmarkUrl() {
        if (webView == null) return null;
        String url = webView.getUrl();
        return url != null && isAllowedUrl(Uri.parse(url)) ? url : null;
    }
    private void updateBookmarkButton() {
        if (bookmarkButton == null) return;
        String url = currentBookmarkUrl();
        boolean available = url != null;
        bookmarkButton.setEnabled(available);
        bookmarkButton.setText(available && new BookmarkStore(this).contains(url) ? "★保存済" : "☆保存");
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
                else setStatus("許可対象外への遷移を遮断しました。");
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
    private void loadSafe(String url) { pageHadIssue = false; setStatus("読み込み中…"); webView.loadUrl(url); }
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
        String lower = value.toLowerCase(Locale.ROOT); return lower.contains("gofile") || lower.contains("twimg") || lower.contains("mvfile");
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
    private boolean isResolverUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && isResolverHost(uri.getHost());
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
        customFullScreenView = view; customFullScreenCallback = callback; view.setBackgroundColor(Color.BLACK);
        root.setVisibility(View.GONE);
        ((ViewGroup) getWindow().getDecorView()).addView(view, new ViewGroup.LayoutParams(-1, -1));
        UiChrome.enterPlayerFullScreen(this);
    }
    private void hideCustomFullScreen() {
        if (customFullScreenView == null) return;
        ViewGroup parent = (ViewGroup) customFullScreenView.getParent(); if (parent != null) parent.removeView(customFullScreenView);
        customFullScreenView = null; root.setVisibility(View.VISIBLE);
        UiChrome.showSystemBars(this);
        WebChromeClient.CustomViewCallback callback = customFullScreenCallback; customFullScreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }
    private void setStatus(String message) {
        if (destroyed) return;
        statusView.setText(message);
        statusView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }
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
        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            pageHadIssue = false; setStatus("読み込み中…"); updateBookmarkButton();
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return !isAllowedUrl(request.getUrl());
            return handleTopLevelNavigation(request.getUrl());
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleTopLevelNavigation(Uri.parse(url)); }
        private boolean handleTopLevelNavigation(Uri uri) {
            if (isResolverUrl(uri)) {
                openRequestedUrl(uri.toString());
                return true;
            }
            navigationGeneration++;
            if (isAllowedUrl(uri)) return false;
            pageHadIssue = true; setStatus("許可対象外への遷移を遮断しました。"); return true;
        }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (isAllowedResourceUrl(request.getUrl())) return null;
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        @Override public void onPageFinished(WebView view, String url) {
            cleanupPage(); CookieManager.getInstance().flush();
            if (isAllowedUrl(Uri.parse(url))) {
                if (!urlInput.hasFocus()) urlInput.setText(url);
                if (!pageHadIssue) setStatus("");
            }
            updateBookmarkButton();
        }
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                pageHadIssue = true; setStatus("ページを読み込めませんでした。通信状態や許可条件を確認してください。");
            }
        }
        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); pageHadIssue = true; setStatus("証明書エラーのため読み込みを中止しました。");
        }
    }
    private static final class ResolveResult {
        final String finalUrl, error;
        private ResolveResult(String url, String error) { this.finalUrl = url; this.error = error; }
        static ResolveResult ok(String url) { return new ResolveResult(url, null); }
        static ResolveResult error(String error) { return new ResolveResult(null, error); }
    }
}
