# Gofile Safe Viewer

Android向けの実験用セーフビューアです。

## 目的

Gofile系リンクを通常ブラウザで直接開かず、ホワイトリスト制のWebViewで確認するための実験アプリです。

## v0.7の仕様

- Android Java / WebView 実装
- GitHub Actionsでdebug APKを正規ビルド
- URL貼り付けから起動
- Android共有メニューから `text/plain` を受け取り
- `https://` かつ URL文字列に `gofile` または `twimg` を含むURLを許可
- `t.co` はWebViewで開かず、HTTPヘッダーのリダイレクトだけを追跡し、展開後URLが許可条件に合うときだけ開く
- Androidのリンク候補として `t.co` と主要な `twimg.com` 系ホストを追加
- 表示検証用に `*.fun800.click` のサムネイル/動画系リソースを一時許可
- 制限付きJavaScriptは初期ON
- ログ表示欄は削除し、内部Logcatのみへ出力
- アプリ側の「画面最大」ボタンで、縦向きのままWebView/プレイヤー領域を最大化
- アプリ側では横画面を強制しない
- フローティング誘導要素をCSS/JS注入で非表示
- 許可条件に合わない外部遷移、外部リソース、popup/window.open、自動ダウンロード、SSLエラーを遮断
- SafeView化したアプリアイコンを設定
- セッション終了時にCookie / WebStorage / Cacheを削除

## 注意

v0.2以降では、検証のために許可条件を緩めています。

```text
許可: HTTPS かつ URL文字列に gofile または twimg を含むURL
追加リソース許可: *.fun800.click
拒否: HTTP、または許可条件に合わないURL
```

これは `https://example.com/?q=gofile` のようなURLも通すため、最終的な安全設計ではありません。最終的には、検出された実ドメインを見て必要なものだけ個別許可する方式へ戻す想定です。

## Xから開く導線

- Xの共有メニューから本アプリを選ぶ
- `t.co` リンクを開く候補に本アプリが出る場合は選ぶ
- `pbs.twimg.com` / `video.twimg.com` などの `twimg.com` 系URLは直接貼り付けても開ける

Android側やX側の挙動により、必ず本アプリが自動起動するわけではありません。

## 制限

- Gofile公式アプリではありません。
- 生のGofile系リンクや `t.co` リンクを必ずこのアプリで開けるわけではありません。Android側・X側・既定ブラウザ設定に左右されます。
- AndroidのIntent Filterでは「URL文字列にgofile/twimgを含む場合だけ候補に出す」という指定はできません。任意URLは共有または貼り付けで検証してください。
- v0.7ではJavaScriptを初期ONにしたため、v0.1より安全側ではなく表示検証側に寄せています。
- v0.7では安全なダウンロード保存機能は未実装です。
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
