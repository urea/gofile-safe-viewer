# APK直接配布とアプリ内更新

Google Playは使用しません。配布先はこのリポジトリのGitHub Releasesです。

## 初回のみ必要な署名設定

GitHub ActionsのRepository secretsに次の4項目を登録します。

- `GSV_KEYSTORE_BASE64`: 固定署名鍵のkeystoreファイルをBase64化した文字列
- `GSV_KEYSTORE_PASSWORD`: keystoreのパスワード
- `GSV_KEY_ALIAS`: 鍵のエイリアス
- `GSV_KEY_PASSWORD`: 鍵のパスワード

配布APKと一緒に所有者専用の署名バックアップを受け取った場合、必ずその鍵を使用してください。新しい鍵を作り直すと、そのAPKから上書き更新できません。鍵・パスワード・secrets.envは公開リポジトリ、Actionsの成果物、公開リリースに置かないでください。暗号化したバックアップを別途保管してください。

GitHub CLIでログイン済みなら、所有者専用バックアップ内のファイルを指定して登録できます。

```sh
gh secret set --repo urea/gofile-safe-viewer --env-file /private/path/secrets.env
```

GitHubのWeb画面では、Settings > Secrets and variables > Actions > New repository secretから登録します。この会話のGitHub接続からはSecretsの登録・取得を操作できないため、この登録だけは所有者の操作が必要です。

登録後、対象コミットの `Android Release APK` の実行をRe-run all jobsで再実行してください。通常CIのdebug成果物を配布しないでください。署名設定がない場合、release workflowは公開をスキップし、Summaryに未設定を記載します。キー未設定時に一時鍵で公開するフォールバックはありません。

## 次回以降

`app/build.gradle`のversionCodeを増やし、versionNameを変更します。`docs/release-notes.md`を更新し、mainまたは既存の作業ブランチ `chatgpt/android-safe-viewer-v0.1` にpushします。

Release workflowは単体テスト・lint・releaseビルドの後、固定鍵で署名されたAPKを検証し、未公開のバージョンだけをReleasesに公開します。既に同名タグのリリースがある場合、資産を上書きしません。タグ名とバージョンを必ず揃えてください。署名鍵は変更しないでください。

配布物は以下の3点です。

- `GofileSafeViewer.apk`
- `update.json`: スキーマ、アプリ識別子、versionCode、versionName、最小API、APKサイズ、SHA-256、固定タグのAPK URL、変更内容
- `signature.txt`: 公開可能な署名証明書情報。秘密鍵ではありません。

アプリは `https://github.com/urea/gofile-safe-viewer/releases/latest/download/update.json` を手動確認します。公開前の404は「更新版はまだ公開されていません」と表示します。

## 保護と制約

更新通信はWebViewのCookie・ログイン情報・閲覧URLを読み出さない専用HTTPクライアントです。HTTPS、このリポジトリのrelease URL、およびリポジトリIDに対応したGitHub配信CDNに限定して転送を追跡します。一般閲覧の許可リストにGitHubは追加していません。

APKはサイズ・SHA-256・パッケージ名・versionCode・署名証明書を照合し、一致時だけ読み取り専用ContentProvider経由でAndroidのインストール画面へ渡します。OSの最終署名検証と利用者の承認を省略しません。サイレント更新は実装していません。署名鍵ローテーションには未対応です。

v0.9以前のdebug APKは署名鍵が異なる場合があり、配布版への初回切り替えだけアンインストールが必要になります。その場合、既存のCookieなどは消えます。v0.10以降の同一鍵による通常更新では端末内のアプリデータを保持します。

debug版は `.debug` の別applicationIdでビルドします。配布版と共存する検証用であり、アプリ内更新の対象ではありません。

## 検証

```sh
python3 scripts/test_release_manifest.py
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

更新URLの偽装、HTTP、異なるリポジトリ、サイズ上限、不正ハッシュ、異なる署名、バージョン比較、不正な更新メタデータをテストします。実機でのXログイン・Cookie維持・インストール許可の往復・上書き更新は別途確認が必要です。
