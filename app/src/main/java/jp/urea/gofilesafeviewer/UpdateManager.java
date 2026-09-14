package jp.urea.gofilesafeviewer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity-independent worker, retained across rotation. All UI state changes run on the main thread. */
final class UpdateManager {
    enum Stage { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }
    interface Listener { void changed(); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile HttpURLConnection activeConnection;
    private Listener listener;
    private Stage stage = Stage.IDLE;
    private String message = "更新確認はボタンを押したときだけ行います。";
    private Release release;
    private File readyFile;

    UpdateManager(Context context) { this.context = context.getApplicationContext(); }
    void attach(Listener value) { listener = value; if (value != null) value.changed(); }
    void detach() { listener = null; }
    Stage stage() { return stage; }
    String message() { return message; }
    Release release() { return release; }
    File readyFile() { return readyFile; }
    boolean busy() { return stage == Stage.CHECKING || stage == Stage.DOWNLOADING; }
    private void state(Stage next, String text) {
        stage = next; message = text;
        if (listener != null) listener.changed();
    }
    private void post(Runnable work) { main.post(() -> { if (!closed) work.run(); }); }

    void check() {
        if (busy()) return;
        release = null; readyFile = null;
        state(Stage.CHECKING, "GitHubで更新を確認しています…");
        worker.execute(() -> {
            try {
                String json;
                HttpURLConnection connection = connect(UpdatePolicy.MANIFEST_URL, "update.json");
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (closed || Thread.currentThread().isInterrupted()) throw new IOException("確認を中止しました。");
                        if (out.size() + count > UpdatePolicy.MAX_MANIFEST_BYTES) throw new IOException("更新情報が大きすぎます。");
                        out.write(buffer, 0, count);
                    }
                    json = new String(out.toByteArray(), StandardCharsets.UTF_8);
                } finally { connection.disconnect(); activeConnection = null; }
                final Release found = Release.parse(json);
                post(() -> {
                    release = found;
                    if (!UpdatePolicy.isNewer(found.versionCode, BuildConfig.VERSION_CODE)) {
                        state(Stage.CURRENT, found.versionCode == BuildConfig.VERSION_CODE
                                ? "最新版です（v" + found.versionName + "）。"
                                : "配布中の版より新しいバージョンを使用しています。");
                    } else if (found.minSdk > Build.VERSION.SDK_INT) {
                        state(Stage.ERROR, "更新版にはAndroid API " + found.minSdk + "以上が必要です。");
                    } else if (BuildConfig.DEBUG) {
                        state(Stage.ERROR, "更新版 v" + found.versionName + "があります。検証用debug版は配布版と署名が異なるため、アプリ内更新できません。");
                    } else {
                        state(Stage.AVAILABLE, "更新版 v" + found.versionName + "があります。更新内容を確認して進めてください。");
                    }
                });
            } catch (NoReleaseException ex) {
                post(() -> state(Stage.ERROR, "更新版はまだGitHub Releasesに公開されていません。"));
            } catch (Exception ex) {
                post(() -> state(Stage.ERROR, safeMessage(ex, "更新を確認できませんでした。通信状態と配布設定を確認してください。")));
            }
        });
    }

    void download() {
        if (stage != Stage.AVAILABLE || release == null || busy()) return;
        final Release target = release;
        readyFile = null;
        state(Stage.DOWNLOADING, "更新APKを取得しています… 0%");
        worker.execute(() -> {
            File temporary = new File(context.getCacheDir(), "updates/update.part");
            File complete = new File(context.getCacheDir(), "updates/update.apk");
            try {
                File directory = temporary.getParentFile();
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("更新用の保存領域を作成できません。");
                if (complete.exists() && !complete.delete()) throw new IOException("前回の更新ファイルを削除できません。");
                HttpURLConnection connection = connect(target.apkUrl, "GofileSafeViewer.apk");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0; int lastPercent = -1;
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (closed || Thread.currentThread().isInterrupted()) throw new IOException("取得を中止しました。");
                        total += count;
                        if (total > target.size || total > UpdatePolicy.MAX_APK_BYTES) throw new IOException("更新APKのサイズが配布情報と一致しません。");
                        out.write(buffer, 0, count); digest.update(buffer, 0, count);
                        final int percent = (int) (total * 100 / target.size);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            post(() -> state(Stage.DOWNLOADING, "更新APKを取得しています… " + percent + "%"));
                        }
                    }
                    out.getFD().sync();
                } finally { connection.disconnect(); activeConnection = null; }
                if (total != target.size || !UpdatePolicy.hex(digest.digest()).equalsIgnoreCase(target.sha256)) {
                    throw new IOException("更新APKの整合性確認に失敗しました。インストールしません。");
                }
                verifyApk(temporary, target);
                if (!temporary.renameTo(complete)) throw new IOException("更新APKを保存できません。");
                post(() -> { readyFile = complete; state(Stage.READY, "取得・検証が完了しました。Androidの確認画面で更新してください。"); });
            } catch (Exception ex) {
                temporary.delete(); complete.delete();
                post(() -> { readyFile = null; state(Stage.ERROR, safeMessage(ex, "APKを取得できませんでした。もう一度更新確認からやり直してください。")); });
            }
        });
    }

    private static String safeMessage(Exception ex, String fallback) {
        // Only our own Japanese messages are shown. Network exception messages can contain URLs.
        String text = ex.getMessage();
        return ex instanceof IOException && text != null && text.matches(".*[ぁ-んァ-ン一-龯].*") ? text : fallback;
    }

    private HttpURLConnection connect(String initial, String asset) throws IOException {
        String next = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (closed || !UpdatePolicy.allowsRedirect(next, asset)) throw new IOException("更新ファイルの接続先が許可されていません。");
            HttpURLConnection c = (HttpURLConnection) new URL(next).openConnection();
            activeConnection = c;
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false); c.setUseCaches(false);
            c.setRequestProperty("User-Agent", "GofileSafeViewer-Updater");
            c.setRequestProperty("Accept", "update.json".equals(asset) ? "application/json" : "application/octet-stream");
            c.setRequestProperty("Accept-Encoding", "identity");
            // This client never reads WebView cookies, browsing URLs or login tokens.
            int code;
            try { code = c.getResponseCode(); } catch (IOException ex) { c.disconnect(); throw ex; }
            if (code == 200) return c;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = c.getHeaderField("Location"); c.disconnect();
                if (location == null) throw new IOException("更新サーバーの転送情報が不正です。");
                next = new URL(new URL(next), location).toString();
                continue;
            }
            c.disconnect(); activeConnection = null;
            if (code == 404 && "update.json".equals(asset)) throw new NoReleaseException();
            throw new IOException("更新サーバーに接続できません（HTTP " + code + "）。");
        }
        throw new IOException("更新サーバーの転送回数が上限を超えました。");
    }

    @SuppressWarnings("deprecation")
    private void verifyApk(File file, Release expected) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        PackageInfo downloaded = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (downloaded == null || !context.getPackageName().equals(downloaded.packageName)) throw new IOException("更新APKのアプリ識別子が一致しません。");
        long version = Build.VERSION.SDK_INT >= 28 ? downloaded.getLongVersionCode() : downloaded.versionCode;
        if (version != expected.versionCode || !UpdatePolicy.isNewer(version, BuildConfig.VERSION_CODE)) throw new IOException("更新APKのバージョンが不正です。");
        if (!UpdatePolicy.sameSigners(signers(installed), signers(downloaded))) throw new IOException("更新APKの署名が一致しません。上書き更新を中止しました。");
        // Android's installer performs the final cryptographic APK verification.
    }

    @SuppressWarnings("deprecation")
    private static byte[][] signers(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null) return null;
        byte[][] result = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) result[i] = signatures[i].toByteArray();
        return result;
    }

    void close() {
        closed = true; listener = null; worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        HttpURLConnection c = activeConnection;
        if (c != null) c.disconnect();
    }
    private static final class NoReleaseException extends IOException { }

    static final class Release {
        final long versionCode, size;
        final int minSdk;
        final String versionName, apkUrl, sha256, notes;
        private Release(JSONObject json) throws Exception {
            versionCode = json.getLong("versionCode"); versionName = json.getString("versionName");
            minSdk = json.getInt("minSdk"); size = json.getLong("sizeBytes");
            apkUrl = json.getString("apkUrl"); sha256 = json.getString("sha256");
            notes = json.optString("releaseNotes", "更新内容の記載はありません。");
            if (json.getInt("schemaVersion") != 1 || !UpdatePolicy.APPLICATION_ID.equals(json.getString("applicationId"))
                    || versionCode < 1 || versionCode > Integer.MAX_VALUE || minSdk < 23 || minSdk > 100
                    || !UpdatePolicy.validSize(size) || !UpdatePolicy.validRelease(versionName, apkUrl)
                    || !UpdatePolicy.validSha256(sha256) || notes.length() > 8000) throw new IOException("更新情報の形式が不正です。");
        }
        static Release parse(String json) throws Exception { return new Release(new JSONObject(json)); }
    }
}
