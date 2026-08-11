package com.example.traning.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * デプロイ時にDBマイグレーションだけを適用するための、Springコンテキストを介さない独立したエントリポイント。
 *
 * <p>Spring BootのFlyway自動設定（{@code spring.flyway.enabled=true}）を使うと、本プロジェクトの JPA設定（{@code
 * spring-boot-starter-data-jpa}によるスキーマ検証用の {@code entityManagerFactory}）との間で {@code Circular
 * depends-on relationship between 'flyway' and 'entityManagerFactory'}
 * が発生し起動できないため、Springを一切経由せずFlywayのJava APIを 直接呼び出す方式にしている。
 *
 * <p>接続情報はSpring本体と同じ環境変数（{@code SPRING_DATASOURCE_URL} / {@code SPRING_DATASOURCE_USERNAME} /
 * {@code SPRING_DATASOURCE_PASSWORD}）から読み取るため、 本番の {@code trainingapp.env}・ローカルの{@code
 * application-local.properties}相当の値と 常に一致する。
 *
 * <p>呼び出し方: {@code java -jar trainingapp.jar --migrate-only=true} （{@link
 * com.example.traning.TraningAppApplication#main}がこの引数を検知し、 SpringApplicationを起動せずこのクラスへ委譲する）
 */
public final class FlywayMigrateCli {

  private FlywayMigrateCli() {}

  /** 本番DBは V16（current_goal_mode追加）まで適用済みのため、これをベースラインとする。 */
  private static final String BASELINE_VERSION = "16";

  /** MySQL Connector/Jのデフォルト接続タイムアウトは無制限のため、明示的に上限を設定する。 */
  private static final int CONNECT_TIMEOUT_MILLIS = 15_000;

  public static int run() {
    String url =
        withConnectTimeout(
            System.getenv()
                .getOrDefault(
                    "SPRING_DATASOURCE_URL",
                    "jdbc:mysql://localhost:3306/training_db?serverTimezone=Asia/Tokyo"));
    String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "root");
    String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    System.out.println("[FlywayMigrateCli] 接続先: " + url);

    Flyway flyway =
        Flyway.configure()
            .dataSource(url, username, password)
            .connectRetries(0)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion(BASELINE_VERSION)
            .load();

    MigrateResult result = flyway.migrate();
    System.out.println(
        "[FlywayMigrateCli] マイグレーション完了: 適用数="
            + result.migrationsExecuted
            + ", 成功="
            + result.success);
    return result.success ? 0 : 1;
  }

  /** JDBC URLに {@code connectTimeout} が未指定なら付与し、DB到達不能時に無限に待ち続けるのを防ぐ。 */
  private static String withConnectTimeout(String url) {
    if (url.contains("connectTimeout=")) {
      return url;
    }
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "connectTimeout=" + CONNECT_TIMEOUT_MILLIS;
  }
}
