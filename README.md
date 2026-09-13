# Gofile Safe Viewer

Android向けの実験用セーフビューアです。

## 目的

`gofile.video` / `gofile.io` のリンクを、通常ブラウザで直接開かず、ホワイトリスト制のWebViewで確認するための実験アプリです。

## v0.1の仕様

- Android Java / WebView 実装
- GitHub Actionsでdebug APKを正規ビルド
- URL貼り付けから起動
- Android共有メニューから `text/plain` を受け取り
- `https://gofile.video` / `https://*.gofile.video` / `https://gofile.io` / `https://*.gofile.io` のみ許可
- `t.co` はWebViewで開かず、HTTPヘッダーのリダイレクトだけを追跡し、展開後がGofile系のときだけ開く
- JavaScriptは初期OFF
- 必要時だけ制限付きJSをONにできる
- 外部遷移、外部リソース、popup/window.open、自動ダウンロード、SSLエラーを遮断
- セッション終了時にCookie / WebStorage / Cacheを削除

## 制限

- Gofile公式アプリではありません。
- 生の `gofile.video` リンクを必ずこのアプリで開けるわけではありません。Android側・X側・既定ブラウザ設定に左右されます。
- JavaScript OFFでは対象ページが正常表示されない可能性があります。
- v0.1では安全なダウンロード保存機能は未実装です。
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
