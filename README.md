# Gofile Safe Viewer

**Xからリンク先まで、アプリ内で。閲覧先を制限するAndroidビューア。**

Xの投稿やGofile系のリンクを閲覧するための、Android向けWebViewアプリです。許可リストをもとにページの遷移や外部リソースの読み込みを制限し、ポップアップやページからのダウンロード、一部の誘導表示を抑えます。

**[最新版APKをダウンロード](https://github.com/urea/gofile-safe-viewer/releases/latest/download/GofileSafeViewer.apk)** · [リリース・変更履歴](https://github.com/urea/gofile-safe-viewer/releases) · [不具合を報告](https://github.com/urea/gofile-safe-viewer/issues)

対応：Android 6.0以上（API 23以上）／APK直接配布／X・Gofileの非公式アプリ

> **サイトやファイル自体の安全性を保証するアプリではありません。** 現在の許可リストには検証用の緩い条件が含まれます。利用前に「許可リストと制限」を確認してください。

## できること

- **Xからそのまま閲覧**：アプリ内でXを開き、投稿や許可対象のリンク先を閲覧できます。ログイン状態は端末内に保持します。
- **共有・貼り付けから開く**：ほかのアプリの共有メニューやURLの貼り付けに対応。Xの短縮URL `t.co` も扱います。
- **閲覧時の不要な動作を制限**：許可対象外への遷移や外部リソース、ポップアップ、ページからのダウンロードを制限します。動画プレーヤーの全画面表示には対応しています。
- **アプリ内から更新**：GitHub Releasesで新しい版を確認し、APKを取得・検証して、Androidの確認画面から上書き更新できます。

任意のサイトを開ける汎用ブラウザや、動画・ファイルを保存するダウンローダーではありません。

## インストール

1. [最新版のリリース](https://github.com/urea/gofile-safe-viewer/releases/latest)から、`GofileSafeViewer.apk` を取得します。
2. Android端末でAPKを開きます。必要な場合は、APKを開いたブラウザやファイル管理アプリに「不明なアプリのインストール」を許可します。
3. Androidの確認画面でインストールし、Gofile Safe Viewerを起動します。

Google Playへの登録や、GitHubへのログイン、署名鍵の設定は、**アプリを利用するだけなら不要**です。`update.json`・`signature.txt`・ソースコードのZIPをインストールする必要はありません。

## 使い方

### Xから閲覧する

ホームの **「Xを開く」** を押します。必要に応じて、開いたXの画面内でログインしてください。ログインを推奨しますが、必須ではありません。X側の仕様により、未ログインでは利用できない機能があります。

投稿内のリンクを開くと、許可条件に合うページをアプリ内で表示します。閲覧画面の「ホーム」から、説明・保存データの削除・更新確認へ戻れます。

### URLを直接開く

ホームの入力欄にURLを貼り付けて **「リンクを開く」** を押すか、ほかのアプリの共有メニューから本アプリを選びます。対応リンクを開く候補に表示された場合も利用できます。候補への表示はAndroidや共有元アプリの設定・挙動に依存します。

### 保存したログイン状態を消す

ホームの **「ログイン・閲覧データを削除」** から、Cookie・WebStorage・キャッシュを削除できます。Xなどのログイン状態も解除されるため、次回はログインし直してください。通常のアプリ終了時には削除しません。

## アプリ内アップデート

ホームの **「更新を確認」→「更新する」→「取得して更新」** から進めます。取得したAPKを検証した後、Androidの確認画面で更新を承認してください。初回は、本アプリからのインストールを許可する設定が必要になる場合があります。

更新前に、APKのサイズ・SHA-256・アプリ識別子・バージョン・署名を照合します。検証に失敗した場合は更新を中止し、Android側の最終検証や利用者の承認も省略しません。更新確認・ダウンロード・インストールを勝手に開始することはありません。

**v0.10.0以降の配布版は、同じ署名鍵による上書き更新に対応しています。通常の更新でアンインストールは不要です。** 端末内のアプリデータを保持します。ただし、X側のセッション失効などによって再ログインが必要になる場合はあります。

<details>
<summary>v0.9以前の試作版、またはdebug版を使用している場合</summary>

v0.9以前は現在の配布版と署名鍵が異なる場合があり、初回の移行時だけ入れ直しが必要になることがあります。アンインストールすると、それまでのアプリ内データは削除されます。これは旧試作版からの移行時の話であり、v0.10.0以降の通常更新とは異なります。

現在のdebug版は、配布版とは別のアプリ識別子で作られる検証用です。配布版の更新には使わず、通常利用にはReleasesの `GofileSafeViewer.apk` を使用してください。

</details>

## 許可リストと制限

### 現在の許可条件

ネットワーク上のURLはHTTPSを対象に、次の条件で判定します。

| 対象 | 判定方法 | 用途 |
| --- | --- | --- |
| `x.com` とそのサブドメイン | ホスト名を判定 | Xのページ・関連リソース |
| `t.co` | ホスト名の完全一致 | Xの短縮リンク |
| `gofile`、`twimg`、または `mvfile` を含むURL | **URL全体の文字列一致（暫定）** | Gofile系ページ、画像・動画など |
| `fun800.click` とそのサブドメイン | ホスト名を判定 | ページ内リソースのみ、一時的に追加許可 |

貼り付け・共有で受け取った `t.co` は、HTTPヘッダーの転送先を確認してから開きます。アプリ内のXページからの `t.co` 遷移にも対応し、転送先にも既存の許可条件を適用します。`data:`・許可条件に合う `blob:` は、ページ内リソースとして扱います。

**Gofile／twimg／mvfileの判定は、厳密なドメイン許可リストではありません。** 例えば `https://example.com/?q=gofile` のように、無関係なドメインでもURLに対象文字列が含まれると許可されます。許可されたURLであることと、安全なサイトであることは別です。

### 保護の範囲

WebViewの設定・コールバックで、許可対象外の遷移やリソース、ポップアップ、ページからのダウンロード、HTTPとの混在、証明書エラーを制限します。一部のフローティング誘導要素も非表示にしますが、**すべての広告を消すアプリではありません。**

これは端末の全通信を監視するファイアウォールや、すべてのWeb通信を厳密に仲介するプロキシではありません。JavaScriptは有効で、ページ自体の動作や内容を無害化するものでもありません。許可されていない外部ドメインに依存する機能は、正常に動かない場合があります。

## ログイン情報・プライバシー

**本アプリには、ログイン情報や閲覧内容を開発者のサーバー・アクセス解析サービスへ収集送信する処理はありません。** 独自のログインフォームでパスワードを集めるのではなく、Xの画面内でログインします。

ログイン状態を保持するCookieやWebStorageは端末内に保存し、通常終了時には削除しません。Androidのアプリデータバックアップは無効にしています。保存データはホームから明示的に削除できます。

ログイン・閲覧に必要な通信は、Xやリンク先などの利用先サービスと行います。更新確認・APK取得ではGitHubへ接続します。**「外部へ一切通信しない」という意味ではありません。** WebViewやOSによる安全性確認などの通信は、各提供元の仕様に従います。

更新処理は閲覧用WebViewと分離しており、WebViewのCookie・閲覧URL・ログイン情報を読み出して送信しません。更新のためにGitHubを閲覧用の許可リストへ追加することもありません。

## 動作確認・不具合報告

利用者のAndroid実機でUIの確認と、v0.10.0からv0.11.0へのアプリ内アップデート成功を確認しています。すべての端末・Androidバージョン・サイト機能の動作を保証するものではありません。

不具合は [Issues](https://github.com/urea/gofile-safe-viewer/issues) へ、アプリのバージョン、Androidのバージョン、端末名、再現手順、表示されたエラーを添えて報告してください。**パスワード・Cookie・認証コード・限定公開リンクなどの秘密情報は記載しないでください。** スクリーンショットも、個人情報が写っていないことを確認してください。

## 開発・ビルド

<details>
<summary>ソースコード、ビルド手順、署名・配布の詳細</summary>

現在の開発用ソースは [`chatgpt/android-safe-viewer-v0.1`](https://github.com/urea/gofile-safe-viewer/tree/chatgpt/android-safe-viewer-v0.1) ブランチにあります。ブランチ名の `v0.1` は現在のアプリバージョンではありません。公開版と同じソースを確認する場合は、各リリースに対応するタグを使用してください。

Android Java／WebViewで実装しています。ビルド環境はJDK 17、Gradle 8.10.2、Android SDK Platform 35・Build Tools 35.0.0です。`minSdk` は23、`compileSdk`・`targetSdk` は35です。

```sh
git clone --branch chatgpt/android-safe-viewer-v0.1 --single-branch https://github.com/urea/gofile-safe-viewer.git
cd gofile-safe-viewer
python3 scripts/test_release_manifest.py
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebug assembleRelease
```

GradleとAndroid SDKを利用できる環境で実行してください。GitHub Actionsでもテスト・lint・APKビルドを行います。

| 主なファイル | 役割 |
| --- | --- |
| `HomeActivity.java` | ホーム画面、データ削除、更新操作 |
| `SafeActivity.java` | WebView、URL判定、共有・短縮リンク処理 |
| `UiChrome.java` | システムバー配色、画面の余白、全画面表示の制御 |
| `UpdateManager.java` / `UpdatePolicy.java` | 更新情報取得、取得先の制限、APKの検証 |
| `UpdateApkProvider.java` | 検証済みAPKをAndroidのインストール画面へ渡す |

Javaファイルは `app/src/main/java/jp/urea/gofilesafeviewer/` 配下です。旧 `MainActivity.java` は残っていますが、現在の起動先ではありません。

`Android Debug APK` workflowは検証用debug APKと未署名release APKを生成します。debug版のアプリ識別子は `jp.urea.gofilesafeviewer.debug`、配布版は `jp.urea.gofilesafeviewer` です。未署名APKはそのままインストールできません。

`Android Release APK` workflowは固定署名鍵を用いてビルドし、未公開のバージョンをGitHub Releasesへ公開します。このリポジトリの署名・配布設定は済んでいます。今後の更新で署名鍵を作り直さず、秘密鍵・パスワードを公開リポジトリや成果物に含めないでください。

配布の詳細は [APK直接配布とアプリ内更新](https://github.com/urea/gofile-safe-viewer/blob/chatgpt/android-safe-viewer-v0.1/docs/releasing.md) を参照してください。アプリの利用者に、この署名設定は不要です。

</details>
