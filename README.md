# Gofile Safe Viewer

Android向けの実験用セーフビューアです。

## 目的

Gofile系リンクやXのページを通常ブラウザで直接開かず、許可リスト制のWebViewで確認するための実験アプリです。

## v0.8の変更

- 「制限付きJS」ボタンを削除。JavaScript / DOM StorageはONのまま維持
- アプリ側の「画面最大」ボタンと独自の最大化処理を削除。動画プレーヤー側の全画面表示は維持
- HTTPSの `x.com` とそのサブドメインを許可。URL全体の部分一致ではなく、ホスト名で判定
- Xのページ・同一ドメインのリソース・HTTPSのXを生成元とするblob URLに対応
- Androidのリンク候補に `x.com` / `*.x.com` を追加

## 現在の仕様

- Android Java / WebView 実装。起動先は `SafeActivity`
- GitHub Actionsでdebug APKを正規ビルド
- URL貼り付けから起動
- Android共有メニューから `text/plain` を受け取り
- HTTPSかつURL文字列に `gofile` または `twimg` を含むURL、またはホスト名が `x.com` / `*.x.com` のURLを許可
- `t.co` はWebViewで開かず、HTTPヘッダーのリダイレクトだけを追跡し、展開後URLが許可条件に合うときだけ開く
- Androidのリンク候補として `t.co`、`x.com` と主要な `twimg.com` 系ホストを登録
- 表示検証用に `*.fun800.click` のサムネイル/動画系リソースを一時許可
- JavaScriptはON。popup/window.open、自動ダウンロード、SSLエラーなどの遮断は維持
- ログは内部Logcatのみへ出力
- アプリ側では横画面を強制しない
- フローティング誘導要素をCSS/JS注入で非表示
- 許可条件に合わない外部遷移、外部リソースを遮断
- SafeView化したアプリアイコンを設定
- セッション終了時にCookie / WebStorage / Cacheを削除

## 注意

v0.2以降では、検証のためにGofile / twimgの許可条件を緩めています。v0.8でもこの既存条件は変更していません。

```text
許可: HTTPS かつ URL文字列に gofile または twimg を含むURL
追加許可: HTTPS かつ ホスト名が x.com または .x.com で終わるURL
追加リソース許可: *.fun800.click
拒否: HTTP、または許可条件に合わないURL
```

Gofile / twimgの既存条件は `https://example.com/?q=gofile` のようなURLも通すため、最終的な安全設計ではありません。最終的には、検出された実ドメインを見て必要なものだけ個別許可する方式へ戻す想定です。

今回追加したXの条件は `https://example.com/?q=x.com` や `https://x.com.example.com/` を許可しません。認証情報を含むURLや不正な形式のURLもXの条件では拒否します。ただし、既存のGofile / twimg条件に合うURLは引き続き許可されます。

## Xから開く導線

- Xの共有メニューから本アプリを選ぶ
- `x.com` / `t.co` リンクを開く候補に本アプリが出る場合は選ぶ
- `x.com` や `pbs.twimg.com` / `video.twimg.com` などのURLを直接貼り付けても開ける

Android側やX側の挙動により、必ず本アプリが自動起動するわけではありません。

## 制限

- Gofile / Xの公式アプリではありません。
- 生のGofile系リンクや `x.com` / `t.co` リンクを必ずこのアプリで開けるわけではありません。Android側・X側・既定ブラウザ設定に左右されます。
- AndroidのIntent Filterでは「URL文字列にgofile/twimgを含む場合だけ候補に出す」という指定はできません。任意URLは共有または貼り付けで検証してください。
- JavaScriptをONにしているため、v0.1より安全側ではなく表示検証側に寄せています。
- Xの実機表示・ログイン・動画再生は別途確認が必要です。許可していない外部ドメインに依存する機能は動作しない可能性があります。
- 安全なダウンロード保存機能は未実装です。
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
