package com.example.traning;

import com.example.traning.smarttrainer.task.MonthlySummaryTask;
import com.example.traning.smarttrainer.task.WeeklySummaryTask;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.example.traning"})
public class TraningAppApplication {

  private static final Logger logger = LoggerFactory.getLogger(TraningAppApplication.class);

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

    // デプロイ時のDBマイグレーション専用モード: Springコンテキストを一切起動せず、
    // Flywayのみを直接実行して終了する（JPAとFlywayの循環依存を避けるため）。
    for (String arg : args) {
      if ("--migrate-only=true".equals(arg)) {
        System.exit(com.example.traning.migration.FlywayMigrateCli.run());
        return;
      }
      // 動作確認用: cronを待たず、週次/月次サマリーバッチを即時1回実行して終了する。
      // SecurityConfigのSecurityFilterChain BeanがHttpSecurity(Web層必須)に依存しているため
      // WebApplicationType.NONEにはできない。稼働中のtrainingapp.service(ポート8080)と
      // 衝突しないよう、空きポート(server.port=0)でWebサーバーを起動する。
      // EnvironmentFile(trainingapp.env)は通常起動時と同じものをsourceして使う。
      if ("--run-weekly-summary=true".equals(arg)) {
        System.exit(runTaskOnceAndExit(args, WeeklySummaryTask.class, WeeklySummaryTask::sendWeeklySummary));
        return;
      }
      if ("--run-monthly-summary=true".equals(arg)) {
        System.exit(
            runTaskOnceAndExit(args, MonthlySummaryTask.class, MonthlySummaryTask::sendMonthlySummary));
        return;
      }
    }

    logger.info("=== TraningApp 起開始 ===");

    try {
      // Performance optimizations for startup
      System.setProperty("spring.backgroundpreinitializer.ignore", "true");
      System.setProperty("spring.jpa.defer-datasource-initialization", "true");

      SpringApplication app = new SpringApplication(TraningAppApplication.class);

      // プロファイルは起動引数 --spring.profiles.active=xxx で外部指定する
      // Disable unnecessary auto-configurations for faster startup
      app.setWebApplicationType(org.springframework.boot.WebApplicationType.SERVLET);

      logger.info("Spring Boot アプリケーションを起動中...");
      Environment env = app.run(args).getEnvironment();

      logger.info("=== TraningApp 起動完了 ===");
      String activeProfile = env.getProperty("spring.profiles.active");
      String port = env.getProperty("server.port", "8080");
      String contextPath = env.getProperty("server.servlet.context-path", "");

      logger.info("プロファイル: {}", activeProfile);
      logger.info("ポート: {}", port);
      logger.info("コンテキストパス: {}", contextPath);

    } catch (Exception e) {
      logger.error("TraningApp 起動中に致命的なエラーが発生しました", e);
      System.exit(1);
    }
  }

  /**
   * 稼働中のtrainingapp.service(ポート8080)とは別の空きポートでSpringコンテキストを立ち上げ、 指定タスクを1回実行して終了する。
   *
   * @return 正常終了なら0、失敗なら1（プロセスの終了コードにそのまま使う）
   */
  private static <T> int runTaskOnceAndExit(
      String[] args, Class<T> taskClass, java.util.function.Consumer<T> action) {
    SpringApplication app = new SpringApplication(TraningAppApplication.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);
    String[] argsWithRandomPort = java.util.Arrays.copyOf(args, args.length + 1);
    argsWithRandomPort[args.length] = "--server.port=0";
    ConfigurableApplicationContext context = app.run(argsWithRandomPort);
    try {
      action.accept(context.getBean(taskClass));
      return 0;
    } catch (Exception e) {
      logger.error("{} の即時実行に失敗しました", taskClass.getSimpleName(), e);
      return 1;
    } finally {
      SpringApplication.exit(context);
    }
  }
}
