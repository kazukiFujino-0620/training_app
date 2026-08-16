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

`application-local.properties` に以下が定義済みのため、通常はそのまま起動できる。

| 変数                         | ローカルデフォルト           |
| ---------------------------- | ---------------------------- |
| `SPRING_DATASOURCE_USERNAME` | `root`                       |
| `SPRING_DATASOURCE_PASSWORD` | （空）                       |
| `jasypt.encryptor.password`  | `local-dev-key`              |
| `app.jwt.secret`             | `bG9jYWw...`（開発用固定値） |

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
