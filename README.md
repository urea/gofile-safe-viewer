# Gofile Safe Viewer

Android向けの実験用セーフビューアです。

## 目的

Gofile系リンクを通常ブラウザで直接開かず、ホワイトリスト制のWebViewで確認するための実験アプリです。

## v0.2の仕様

- Android Java / WebView 実装
- GitHub Actionsでdebug APKを正規ビルド
- URL貼り付けから起動
- Android共有メニューから `text/plain` を受け取り
- 緩和モードとして、`https://` かつ URL文字列に `gofile` を含むURLを許可
- `t.co` はWebViewで開かず、HTTPヘッダーのリダイレクトだけを追跡し、展開後URLに `gofile` が含まれるときだけ開く
- JavaScriptは初期OFF
- 必要時だけ制限付きJSをONにできる
- 許可条件に合わない外部遷移、外部リソース、popup/window.open、自動ダウンロード、SSLエラーを遮断
- セッション終了時にCookie / WebStorage / Cacheを削除

## 注意

v0.2では、検証のために許可条件を緩めています。

```text
許可: HTTPS かつ URL文字列に gofile を含むURL
拒否: HTTP、またはURL文字列に gofile を含まないURL
```

これは `https://example.com/?q=gofile` のようなURLも通すため、v0.1の `gofile.video` / `gofile.io` 限定より安全性は落ちます。最終的には、検出された実ドメインを見て必要なものだけ個別許可する方式へ戻す想定です。

## 制限

- Gofile公式アプリではありません。
- 生の `gofile.video` リンクを必ずこのアプリで開けるわけではありません。Android側・X側・既定ブラウザ設定に左右されます。
- AndroidのIntent Filterでは「URL文字列にgofileを含む場合だけ候補に出す」という指定はできません。任意URLは共有または貼り付けで検証してください。
- JavaScript OFFでは対象ページが正常表示されない可能性があります。
- v0.2では安全なダウンロード保存機能は未実装です。
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
