# TraningApp

筋トレ記録・管理 Web アプリケーション（Spring Boot）。  
Web ブラウザでの利用に加え、モバイルアプリ（Expo / React Native）向け REST API も提供する。

---

## 技術スタック

| カテゴリ             | 使用技術                                                    |
| -------------------- | ----------------------------------------------------------- |
| 言語                 | Java 21                                                     |
| フレームワーク       | Spring Boot 3.4.2                                           |
| ORM                  | Doma2 2.61.0 + Spring Data JPA（スキーマ検証のみ）          |
| DBマイグレーション   | Flyway（デプロイ時に明示適用、アプリ起動時の自動適用はしない）|
| テンプレートエンジン | Thymeleaf                                                   |
| データベース         | MySQL 8.0                                                   |
| 認証                 | Spring Security、Google OAuth2、LINE Login、JWT（モバイル） |
| セキュリティ         | Jasypt（設定値暗号化）、Bucket4j（レート制限）、TOTP（MFA） |
| API ドキュメント     | springdoc-openapi（Swagger UI）                             |
| ビルドツール         | Maven Wrapper（`./mvnw`）                                   |

---

## 前提条件

- Java 21
- MySQL 8.0
- Maven（`./mvnw` が同梱されているため不要でも可）

---

## ローカル開発のセットアップ

### 1. データベース作成

```bash
mysql -u root -e "
CREATE DATABASE IF NOT EXISTS training_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"
```

### 2. テーブル作成（Flyway）

`src/main/resources/db/migration/` 内の SQL は Flyway で管理している。以下のコマンドで、未適用のマイグレーションのみが自動的に適用される（適用済みかどうかは `flyway_schema_history` テーブルで管理される）。

```bash
./mvnw package -DskipTests -Plocal
java -jar target/TraningApp-*.jar --migrate-only=true
```

> **注意**: Spring Boot の Flyway 自動設定（`spring.flyway.enabled=true` によるアプリ起動時の自動適用）は使用していない。JPA の `entityManagerFactory` との間で循環依存エラーになるため、`--migrate-only=true` を渡した専用モード（`com.example.traning.migration.FlywayMigrateCli`）で、Spring コンテキストを経由せず直接 Flyway を実行する方式にしている。ローカル・GCP 本番とも同じ仕組みを使う。

### 3. アプリケーション起動

デフォルトプロファイルは `local`（`application-local.properties` が読み込まれる）。

```bash
./mvnw spring-boot:run
```

ブラウザで `http://localhost:8080` を開く。

---

## プロファイル構成

| プロファイル          | 用途         | 設定ファイル                   |
| --------------------- | ------------ | ------------------------------ |
| `local`（デフォルト） | ローカル開発 | `application-local.properties` |
| `gcp`                 | 本番（GCP）  | `application-gcp.properties`   |

本番環境では環境変数 `SPRING_PROFILES_ACTIVE=gcp` で切り替える。

### ローカル開発に必要な環境変数

公開リポジトリのため、鍵の値はリポジトリ内のファイルに書かない（Gitleaks の CI で検出され失敗する）。

#### 1. 設定ファイルに定義済みのもの（追加作業なし）

`application-local.properties` に開発用の値が定義済み。

| 変数                         | ローカルデフォルト |
| ---------------------------- | ------------------ |
| `SPRING_DATASOURCE_USERNAME` | `root`             |
| `SPRING_DATASOURCE_PASSWORD` | （空）             |

#### 2. 自分で環境変数を設定するもの

いずれも既定値なし。未設定のまま `./mvnw spring-boot:run` すると、`Could not resolve placeholder '<変数名>'` で起動に失敗する。

| 環境変数                       | 用途                                          | ローカル用の値の作り方               | 未設定時 |
| ------------------------------ | --------------------------------------------- | ------------------------------------ | -------- |
| `APP_JWT_SECRET`               | モバイル API の JWT 署名鍵（Base64、256bit 以上） | `openssl rand -base64 64 \| tr -d '\n'` | 起動失敗 |
| `APP_SECURITY_REMEMBER_ME_KEY` | Web のログイン状態保持（Remember-me）の署名鍵 | `openssl rand -base64 32 \| tr -d '\n'` | 起動失敗 |
| `JASYPT_ENCRYPTOR_PASSWORD`    | Jasypt（設定値の暗号化）のパスワード          | `openssl rand -base64 32 \| tr -d '\n'` | 起動失敗 |

- Jasypt について: 現在、設定ファイルで暗号化した値（`ENC(...)`）は使っていないため、ローカルでは任意のランダム値で良い。今後ローカル用の設定に `ENC(...)` を入れる場合は、暗号化に使ったパスワードと同じ値を設定しないと復号できない
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `LINE_CLIENT_ID` / `LINE_CLIENT_SECRET` も未設定だと起動失敗する（以前からの仕様）。Google/LINE ログインを試さないときは任意の文字列（例: `dummy`）で起動できる

生成した値をシェルの設定ファイル（`~/.zshrc` など。リポジトリの外）に追記し、新しいターミナルで起動する。

```bash
# ~/.zshrc に追記（<生成した値> をそれぞれ置き換える）
export APP_JWT_SECRET='<生成した値>'
export APP_SECURITY_REMEMBER_ME_KEY='<生成した値>'
export JASYPT_ENCRYPTOR_PASSWORD='<生成した値>'
```

```bash
source ~/.zshrc
# "set" と表示されれば設定済み（値そのものは表示しない）
echo JWT=${APP_JWT_SECRET:+set} REMEMBER_ME=${APP_SECURITY_REMEMBER_ME_KEY:+set} JASYPT=${JASYPT_ENCRYPTOR_PASSWORD:+set}
./mvnw spring-boot:run
```

- IntelliJ / VS Code から起動する場合は、実行構成の環境変数にも同じ3つを設定する
- `APP_JWT_SECRET` を作り直すと、ローカルで発行済みのモバイル用トークンは無効になる（モバイルアプリで再ログインすれば良い）。`APP_SECURITY_REMEMBER_ME_KEY` を作り直すと、ブラウザの「ログイン状態を保持」が解除される（再ログインすれば良い）
- 本番（GCP）の値とは必ず別の値にする。本番の値をローカルに持ち込まない
- `./mvnw test` は Spring コンテキストを起動しないため、これらの環境変数は不要

---

## アクセス URL

### ローカル開発

| 画面       | URL                                   |
| ---------- | ------------------------------------- |
| アプリ     | http://localhost:8080                 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

### 本番（GCP）

| 画面         | URL                                                        |
| ------------ | ---------------------------------------------------------- |
| アプリ       | https://training-app-test.mydns.jp                         |
| OpenAPI JSON | https://kazukifujino-0620.github.io/training_app/api/#/    |
| SmartTrainer | https://kazukifujino-0620.github.io/training_app/#features |

---

## 主な機能

| 機能             | 概要                                               |
| ---------------- | -------------------------------------------------- |
| トレーニング記録 | 種目・セット・重量・回数の記録、スーパーセット対応 |
| テンプレート機能 | よく使う種目構成をテンプレート登録し、日付を指定して一括適用 |
| カレンダー表示   | 月別トレーニング履歴と筋肉マップ                   |
| 自己ベスト管理   | 種目ごとの最高重量・回数を自動記録（同一セットの実測値として連動更新） |
| 目標設定         | 体重・種目別の目標管理                             |
| 組織（マルチテナント）対応 | 組織・店舗単位でのデータスコープ分離（`ROLE_ORG_ADMIN`／`ROLE_STORE_ADMIN`） |
| ヘルスケア連携   | HealthKit（iOS）／Health Connect（Android）と同期し、体重・歩数・心拍数・消費カロリー・睡眠を取得（モバイルのみ、読み取り専用） |
| Smart Trainer    | AI によるトレーニング提案                          |
| 管理者機能       | ユーザー管理、トレーニング集計、監査ログ           |
| モバイル API     | `/api/mobile/` 配下の REST API（JWT 認証）         |
| MFA              | TOTP による二段階認証（バックアップコード対応）    |

---

## プロジェクト構造

```
src/main/java/com/example/traning/
├── training/        # トレーニング記録・メニュー画面
├── template/        # トレーニングテンプレート機能
├── user/            # ユーザー管理・管理者機能
├── organization/    # 組織（マルチテナント）スコープ
├── health/          # ヘルスケア連携（HealthKit / Health Connect）
├── mobile/          # モバイルアプリ向け REST API
├── goal/            # 目標設定
├── pr/              # 自己ベスト（Personal Record）
├── smarttrainer/    # AI トレーナー機能
├── mfa/             # 多要素認証
├── audit/           # 監査ログ
├── config/          # Spring Security 等の設定
└── ...

src/main/resources/db/migration/   # DB マイグレーション SQL（Flyway管理）
expo-app/                          # モバイルアプリ（Expo）
docs/                              # 追加ドキュメント
```

---

## DB マイグレーション（注意事項）

- Flyway で管理している（`org.flywaydb:flyway-core` / `flyway-mysql`）。
- 新しいテーブル追加・カラム変更は `src/main/resources/db/migration/V{n}__説明.sql` にファイルを追加するだけでよい。バージョン番号（`V{n}`）は既存ファイルと重複しないよう注意すること（Flywayはバージョン重複をエラーとして検出する）。
- **アプリ起動時の自動適用ではなく、デプロイ手順内での明示的な適用**（`--migrate-only=true`、上記「テーブル作成」参照）。GCPデプロイでは `.github/workflows/deploy.yml` が、JARの差し替え直後・サービス再起動前に自動実行する（`deploy/trainingapp-migrate.service`、詳細は [DEPLOYMENT.md](DEPLOYMENT.md) 参照）。
- 本番DBは `V16` まで適用済みとしてベースライン設定済み（`FlywayMigrateCli` 内の `BASELINE_VERSION` 定数）。過去に手動適用運用だった際、マイグレーションファイルの存在と本番への実際の適用が一致しないインシデントが複数回発生していたため、Flyway導入によりこれを構造的に防止している。

---

## デプロイ

GCP へのデプロイ手順は [DEPLOYMENT.md](DEPLOYMENT.md) を参照。
