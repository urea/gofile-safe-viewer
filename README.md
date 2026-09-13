# Gofile Safe Viewer

Android向けの実験用セーフビューアです。

## 目的

Gofile系リンクを通常ブラウザで直接開かず、ホワイトリスト制のWebViewで確認するための実験アプリです。

## v0.3の仕様

- Android Java / WebView 実装
- GitHub Actionsでdebug APKを正規ビルド
- URL貼り付けから起動
- Android共有メニューから `text/plain` を受け取り
- 緩和モードとして、`https://` かつ URL文字列に `gofile` を含むURLを許可
- `t.co` はWebViewで開かず、HTTPヘッダーのリダイレクトだけを追跡し、展開後URLに `gofile` が含まれるときだけ開く
- v0.3では表示検証を優先し、制限付きJavaScriptを初期ON
- JS切替時に現在ページを自動リロード
- ページタイトル、100%進捗、読み込みエラーをログに表示
- 許可条件に合わない外部遷移、外部リソース、popup/window.open、自動ダウンロード、SSLエラーを遮断
- セッション終了時にCookie / WebStorage / Cacheを削除

## 注意

v0.2以降では、検証のために許可条件を緩めています。

```text
許可: HTTPS かつ URL文字列に gofile を含むURL
拒否: HTTP、またはURL文字列に gofile を含まないURL
```

これは `https://example.com/?q=gofile` のようなURLも通すため、v0.1の `gofile.video` / `gofile.io` 限定より安全性は落ちます。最終的には、検出された実ドメインを見て必要なものだけ個別許可する方式へ戻す想定です。

## 制限

- Gofile公式アプリではありません。
- 生の `gofile.video` リンクを必ずこのアプリで開けるわけではありません。Android側・X側・既定ブラウザ設定に左右されます。
- AndroidのIntent Filterでは「URL文字列にgofileを含む場合だけ候補に出す」という指定はできません。任意URLは共有または貼り付けで検証してください。
- v0.3ではJavaScriptを初期ONにしたため、v0.1より安全側ではなく表示検証側に寄せています。
- v0.3では安全なダウンロード保存機能は未実装です。
- 目的ファイル自体が安全であることは保証しません。

## ビルド

GitHub Actionsの `Android Debug APK` workflowで `app-debug.apk` を生成します。

手元でビルドする場合はAndroid SDKとGradleを用意し、以下を実行します。

```bash
gradle --no-daemon assembleDebug
```

APK出力先:

```text
app/build/outputs/apk/debug/app-debug.apk
```
