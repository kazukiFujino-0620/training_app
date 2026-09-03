package com.example.traning.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * ita3-3: 登録LP（welcome.html）がSpring Bootコンテキストを起動せずにThymeleafとして正しく解釈できることを検証する。
 * DB/OAuth2設定等の実行環境に依存せず、テンプレート構文（th:href/th:src/th:value等）の妥当性のみを確認する。
 */
class WelcomeTemplateRenderTest {

  private String render(String inviteCode) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");

    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.setDialect(new SpringStandardDialect());

    MockServletContext servletContext = new MockServletContext();
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
    MockHttpServletResponse response = new MockHttpServletResponse();
    JakartaServletWebApplication webApplication =
        JakartaServletWebApplication.buildApplication(servletContext);
    IServletWebExchange exchange = webApplication.buildExchange(request, response);

    WebContext context = new WebContext(exchange, Locale.JAPAN);
    context.setVariable("inviteCode", inviteCode);
    return engine.process("welcome", context);
  }

  @Test
  void 招待コード未指定でもエラーなくレンダリングされる() {
    String html = render("");

    assertThat(html).contains("LIFTLOG");
    assertThat(html).contains("招待コードをお持ちの方");
    assertThat(html).contains("招待コードをお持ちでない方はこちら");
    assertThat(html).contains("href=\"/signup\"");
  }

  @Test
  void 招待コードがフォームの初期値に反映される() {
    String html = render("ABCDEFGHIJ");

    assertThat(html).contains("value=\"ABCDEFGHIJ\"");
  }

  @Test
  void 画像パスが正しく解決される() {
    String html = render("");

    assertThat(html).contains("/images/lp/screen-home.png");
    assertThat(html).contains("/images/lp/qr-download.png");
  }

  @Test
  void プライバシーポリシーへのリンクが実パスになっている() {
    String html = render("");

    assertThat(html).contains("href=\"/privacy\"");
  }
}
