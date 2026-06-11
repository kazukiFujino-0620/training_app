package com.example.traning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.example.traning"})
public class TraningAppApplication {

  private static final Logger logger = LoggerFactory.getLogger(TraningAppApplication.class);

  public static void main(String[] args) {
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
}
