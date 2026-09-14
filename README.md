# Gofile Safe Viewer

Android向けの実験用セーフビューアです。Gofile系リンクやXのページを、許可対象外への遷移などを制限するWebViewで閲覧します。サイトやファイル自体の安全性を保証するアプリではありません。

## v0.10の変更

- 起動先をネイティブのホーム画面に変更。アプリ説明、許可リスト概要、情報の扱いを表示
- Xを開く／Xにログイン／URLを貼り付けて開く導線を追加。外部共有は引き続きビューアへ直接渡す
- ログイン状態のCookie・WebStorageを端末内で保持。ホームから明示的に削除可能
- アクティブなビューアのURL・タイトルのLogcat出力を廃止。認証情報を収集するJavaScript bridgeや解析SDKは導入しない
- 手動の更新確認、更新内容の確認、APK取得と検証、Androidの承認付きインストールに対応
- GitHub Releasesへの固定鍵による署名・公開workflowを追加。秘密鍵未設定なら公開せず、設定待ちを明示
- 以前削除した制限付きJS／画面最大ボタンは復活させない。動画プレーヤーの全画面は維持

## 閲覧の許可条件

```text
許可: HTTPSかつURL全体にgofileまたはtwimgを含む
追加許可: HTTPSかつホスト名がx.com / *.x.com / t.co
追加リソース許可: HTTPSのfun800.click / *.fun800.click
```

Gofile／twimgは検証用の緩い文字列判定のままです。`https://example.com/?q=gofile`のようなURLも通るため、安全性を検証したドメイン一覧ではありません。X／t.coの追加条件はホスト名を解析し、パス・クエリーの文字列だけでは許可しません。

貼り付け・共有からのt.coは既存のHTTPヘッダー展開を使用します。WebView内のt.coへの遷移も許可します。許可対象外のナビゲーション／リソース、ポップアップ、ページからのダウンロード、HTTP混在、証明書エラーの遮断を維持します。WebViewコールバックによる制限であり、全通信を厳密に仲介するプロキシではありません。

## 情報の扱い

本アプリには、ログイン情報や閲覧内容を開発者サーバー・アクセス解析サービスに送信する処理はありません。ログインや閲覧に必要な通信はXなどの利用先と行います。ログイン情報を独自に収集する入力フォームは作らず、Xの公式ログインページを開きます。

Cookie・WebStorageは端末内で保持し、ホームの「ログイン・閲覧データを削除」でCookie・WebStorage・キャッシュを削除できます。Androidバックアップは無効です。WebView・OSの安全性確認等の通信はそれぞれの仕様に従います。

更新確認・APK取得ではGitHubに接続します。更新処理は閲覧用WebViewと分離しており、WebViewのCookie・閲覧URL・ログイン情報を読み出しません。GitHubを閲覧の許可リストに追加することもありません。

## 配布と更新

Google PlayではなくGitHub ReleasesでAPKを直接配布します。アプリの更新は利用者が更新確認を押したときだけ始まり、更新内容確認 → APK取得 → サイズ／SHA-256／アプリID／バージョン／署名照合 → Androidの承認、の順に進みます。

**初回の固定署名鍵の登録が必要です。手順は [docs/releasing.md](docs/releasing.md) を参照してください。** 設定前はrelease workflowが公開をスキップします。debug APKを正式な更新先として配布することはありません。

v0.9以前からの切り替えでは署名が異なり、初回だけ入れ直しが必要になる場合があります。以後は固定署名鍵を使い、端末内データを維持して上書き更新します。

## ビルドとテスト

Android Java／WebView、minSdk 23、targetSdk 35です。起動先はHomeActivity、閲覧はSafeActivityです。旧MainActivityは起動先ではありません。

```sh
python3 scripts/test_release_manifest.py
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

`Android Debug APK` workflowはテスト・lint・debugビルド・未署名releaseビルドを行います。debug版は別applicationIdの検証用です。未署名release APKはそのままインストールできません。`Android Release APK` workflowが固定鍵で署名・公開します。

Xの実機表示・ログイン・動画再生・Cookie維持、Androidのインストール許可と更新フローは実機で別途確認してください。許可していない外部ドメインに依存するページ機能は動作しない場合があります。通常のダウンロード保存機能は未実装です。
