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

### 2. テーブル作成（手動マイグレーション）

> **注意**: このプロジェクトは Flyway の自動実行を使用しない。  
> `src/main/resources/db/migration/` 内の SQL を順番に手動実行すること。

```bash
for f in src/main/resources/db/migration/V*.sql; do
  echo "Applying $f ..."
  mysql -u root training_db < "$f"
done
```

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
| カレンダー表示   | 月別トレーニング履歴と筋肉マップ                   |
| 自己ベスト管理   | 種目ごとの最高重量・回数を自動記録                 |
| 目標設定         | 体重・種目別の目標管理                             |
| Smart Trainer    | AI によるトレーニング提案                          |
| 管理者機能       | ユーザー管理、トレーニング集計、監査ログ           |
| モバイル API     | `/api/mobile/` 配下の REST API（JWT 認証）         |
| MFA              | TOTP による二段階認証                              |

---

## プロジェクト構造

```
src/main/java/com/example/traning/
├── training/        # トレーニング記録・メニュー画面
├── user/            # ユーザー管理・管理者機能
├── mobile/          # モバイルアプリ向け REST API
├── goal/            # 目標設定
├── pr/              # 自己ベスト（Personal Record）
├── smarttrainer/    # AI トレーナー機能
├── mfa/             # 多要素認証
├── audit/           # 監査ログ
├── config/          # Spring Security 等の設定
└── ...

src/main/resources/db/migration/   # DB マイグレーション SQL（手動実行）
expo-app/                          # モバイルアプリ（Expo）
docs/                              # 追加ドキュメント
```

---

## DB マイグレーション（注意事項）

- Flyway は**使用していない**。マイグレーションファイルは記録目的。
- 新しいテーブル追加・カラム変更は必ず `mysql` コマンドで手動実行する。
- GCP デプロイ時も JAR 差し替えと DB マイグレーションを**セット**で実施すること  
  （抜けると `Schema-validation` エラーでアプリが起動不能になる）。

---

## デプロイ

GCP へのデプロイ手順は [DEPLOYMENT.md](DEPLOYMENT.md) を参照。
