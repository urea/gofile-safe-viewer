package jp.urea.gofilesafeviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;

/** Native home; remote pages cannot invoke update or data-deletion operations. */
public final class HomeActivity extends Activity {
    private static final int INSTALL_PERMISSION = 100;
    private static final int INSTALL_APK = 101;
    private UpdateManager updates;
    private TextView updateStatus, updateNotes;
    private Button checkButton, updateButton;
    private boolean resumed, autoInstall;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Object retained = getLastNonConfigurationInstance();
        updates = retained instanceof UpdateManager ? (UpdateManager) retained : new UpdateManager(this);
        autoInstall = saved != null && saved.getBoolean("autoInstall");
        buildHome();
        updates.attach(this::renderUpdate);
    }

    @Override public Object onRetainNonConfigurationInstance() { updates.detach(); return updates; }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("autoInstall", autoInstall);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; renderUpdate(); }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override protected void onDestroy() {
        updates.detach();
        if (!isChangingConfigurations()) updates.close();
        super.onDestroy();
    }

    private void buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setFitsSystemWindows(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(20));
        scroll.addView(page);
        TextView title = text("Gofile Safe Viewer", 23);
        title.setTypeface(null, android.graphics.Typeface.BOLD); page.addView(title);
        page.addView(text("v" + BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? "  検証用" : ""), 13));
        page.addView(text("XやGofile系リンクを、許可対象外への遷移などを制限しながら閲覧するアプリです。ファイルやサイトの安全性を保証するものではありません。", 14));

        section(page, "使い始める");
        page.addView(text("Xへのログインを推奨します。公式のx.comでログインし、そのまま投稿やリンク先を閲覧できます。ログインは必須ではありません。", 14));
        LinearLayout xActions = row(page);
        button(xActions, "Xを開く", () -> open("https://x.com/home"));
        button(xActions, "Xにログイン", () -> open("https://x.com/i/flow/login"));
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setTextSize(14);
        input.setHint("URLを貼り付け（共有からも開けます）");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        page.addView(input, new LinearLayout.LayoutParams(-1, -2));
        Button open = new Button(this); open.setText("リンクを開く");
        open.setOnClickListener(v -> {
            if (input.getText().toString().trim().isEmpty()) input.setError("URLを入力してください。");
            else open(input.getText().toString());
        }); page.addView(open, new LinearLayout.LayoutParams(-1, -2));

        section(page, "許可リストの概要");
        page.addView(text("HTTPSのみ。x.comとそのサブドメイン、t.co、URLにgofileまたはtwimgを含むものを許可しています。fun800.click系は画像・動画等のリソースだけを追加許可しています。", 14));
        page.addView(text("許可対象外への遷移、ポップアップ、自動ダウンロード、証明書エラーを遮断します。Gofile／twimgの判定は現在、検証用の文字列一致です。", 13));

        section(page, "ログイン情報・プライバシー");
        page.addView(text("本アプリはログイン情報や閲覧内容を、開発者のサーバーやアクセス解析サービスへ送信しません。ログイン・閲覧に必要な通信はXなどの利用先へ、更新確認・APK取得の通信はGitHubへ行います。", 14));
        page.addView(text("ログイン画面の入力を独自に収集する処理はありません。ログイン状態を保持するCookie等は端末内に保存し、終了時には削除しません。WebView・OSの安全性確認等の通信は各提供元の仕様に従います。", 13));
        Button clear = new Button(this); clear.setText("ログイン・閲覧データを削除");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("保存データを削除しますか？")
                .setMessage("Xを含む閲覧先からログアウトします。Cookie・WebStorage・キャッシュを削除します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除する", (dialog, which) -> {
                    clear.setEnabled(false);
                    CookieManager.getInstance().removeAllCookies(done -> {
                        CookieManager.getInstance().flush();
                        WebStorage.getInstance().deleteAllData();
                        if (!isDestroyed()) {
                            WebView cacheCleaner = new WebView(this);
                            cacheCleaner.clearCache(true); cacheCleaner.destroy();
                            clear.setEnabled(true);
                            android.widget.Toast.makeText(this, "保存データを削除しました。", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }).show());
        page.addView(clear, new LinearLayout.LayoutParams(-1, -2));

        section(page, "アプリの更新");
        updateStatus = text("", 14); page.addView(updateStatus);
        updateNotes = text("", 13); page.addView(updateNotes);
        LinearLayout actions = row(page);
        checkButton = button(actions, "更新を確認", () -> { autoInstall = false; updates.check(); });
        updateButton = button(actions, "更新する", () -> {
            if (updates.stage() == UpdateManager.Stage.READY) requestInstall();
            else if (updates.release() != null) new AlertDialog.Builder(this)
                    .setTitle("v" + updates.release().versionName + "へ更新")
                    .setMessage(updates.release().notes + "\n\nGitHubからAPKを取得し、検証後にAndroidの更新確認画面を開きます。")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("取得して更新", (dialog, which) -> { autoInstall = true; updates.download(); }).show();
        });
        page.addView(text("自動更新は行いません。取得したAPKを検証し、Androidの確認画面で承認してから更新します。通常の上書き更新では端末内の設定・ログイン状態を保持します。", 13));
        setContentView(scroll);
    }

    private void renderUpdate() {
        if (updateStatus == null || isDestroyed()) return;
        updateStatus.setText(updates.message());
        checkButton.setEnabled(!updates.busy());
        UpdateManager.Stage stage = updates.stage();
        boolean actionable = stage == UpdateManager.Stage.AVAILABLE || stage == UpdateManager.Stage.READY;
        updateButton.setVisibility(actionable ? View.VISIBLE : View.GONE);
        updateButton.setText(stage == UpdateManager.Stage.READY ? "インストール" : "更新する");
        updateNotes.setText(updates.release() == null ? "" : updates.release().notes);
        updateNotes.setVisibility(updates.release() == null ? View.GONE : View.VISIBLE);
        if (stage == UpdateManager.Stage.ERROR) autoInstall = false;
        if (resumed && autoInstall && stage == UpdateManager.Stage.READY) {
            autoInstall = false;
            requestInstall();
        }
    }

    private void requestInstall() {
        File apk = updates.readyFile();
        if (apk == null || !apk.isFile()) {
            updateStatus.setText("更新APKが見つかりません。更新確認からやり直してください。"); return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("インストールの許可")
                    .setMessage("Androidの設定で、このアプリからのインストールを許可してください。戻ると更新確認画面へ進みます。")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("設定を開く", (dialog, which) -> {
                        try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION); }
                        catch (android.content.ActivityNotFoundException ex) { updateStatus.setText("この端末ではインストール許可の設定画面を開けません。"); }
                    }).show(); return;
        }
        try {
            Uri uri = Uri.parse("content://" + getPackageName() + ".updates/update.apk");
            // Explicit platform installer entry point: approval is never bypassed.
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri); install.setClipData(ClipData.newRawUri("update", uri));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(install, INSTALL_APK);
        } catch (android.content.ActivityNotFoundException | SecurityException ex) {
            updateStatus.setText("Androidのインストール画面を開けません。端末のインストール許可を確認してください。");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) requestInstall();
            else updateStatus.setText("インストールは許可されていません。許可後に「インストール」を押してください。");
        } else if (request == INSTALL_APK && result != RESULT_OK) {
            updateStatus.setText("更新は完了していません。キャンセルした場合は「インストール」から再開できます。");
        }
    }

    private void open(String url) {
        Intent intent = new Intent(this, SafeActivity.class);
        intent.setAction(Intent.ACTION_SEND); intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url); startActivity(intent);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 43, 51)); view.setPadding(0, dp(3), 0, dp(3)); return view;
    }
    private void section(LinearLayout parent, String heading) {
        TextView view = text(heading, 17); view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(12), 0, dp(3)); parent.addView(view);
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button view = new Button(this); view.setText(label); view.setTextSize(14);
        view.setOnClickListener(v -> action.run()); parent.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); return view;
    }
}
