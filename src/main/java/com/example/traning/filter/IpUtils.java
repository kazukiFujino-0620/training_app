package com.example.traning.filter;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

  private IpUtils() {}

  /**
   * クライアントIPを解決する。
   *
   * <p>{@code server.forward-headers-strategy=native}（application.properties）により、TomcatのRemoteIpValve相当の機能が
   * 有効化されている。これは直接接続してきたピア（TCP接続元）が信頼できる内部プロキシのIPレンジ（デフォルトでRFC1918の
   * プライベートアドレス・loopback）である場合のみ {@code X-Forwarded-For} を信頼し、{@link HttpServletRequest#getRemoteAddr()}
   * に反映する。信頼できないピアからの接続では {@code X-Forwarded-For} は無視され、実際の接続元IPがそのまま返る。
   *
   * <p>そのため、このメソッド自身で {@code X-Forwarded-For} を直接パースして信頼してはならない（任意の外部クライアントが
   * ヘッダーを詐称してレート制限・IPブロックを回避できてしまうため）。{@code getRemoteAddr()} を使うことで、上記の
   * 信頼境界チェックを経た値のみを利用する。
   */
  public static String resolveClientIp(HttpServletRequest req) {
    return req.getRemoteAddr();
  }
}
