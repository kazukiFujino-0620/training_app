package com.example.traning.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.service.IpBlockService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * レート制限超過時のレスポンス形式を検証する。ブラウザのログインフォーム（POST /login）に対しては
 * JSON文字列をそのまま画面表示させてしまうユーザー体験上の不具合があったため、ログイン画面への
 * リダイレクトに変更した（/api/**等の他エンドポイントは引き続きJSONを返す）。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  @Mock private RateLimitBucketManager bucketManager;
  @Mock private IpBlockService ipBlockService;
  @Mock private Bucket bucket;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RateLimitFilter(bucketManager, ipBlockService);
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
  }

  @Test
  void doFilterInternal_ログインでレート制限超過時はログイン画面へリダイレクトする() throws Exception {
    when(request.getRequestURI()).thenReturn("/login");
    when(request.getMethod()).thenReturn("POST");
    when(ipBlockService.isBlocked("1.2.3.4")).thenReturn(false);
    when(bucketManager.loginBucket("1.2.3.4")).thenReturn(bucket);
    when(bucket.tryConsume(1)).thenReturn(false);
    when(bucket.getAvailableTokens()).thenReturn(0L);

    filter.doFilterInternal(request, response, chain);

    verify(response).sendRedirect("/login?error&reason=rate_limited");
    verify(response, never()).setStatus(429);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternal_IPブロック中のログインもログイン画面へリダイレクトする() throws Exception {
    when(request.getRequestURI()).thenReturn("/login");
    when(request.getMethod()).thenReturn("POST");
    when(ipBlockService.isBlocked("1.2.3.4")).thenReturn(true);

    filter.doFilterInternal(request, response, chain);

    verify(response).sendRedirect("/login?error&reason=rate_limited");
    verify(response, never()).setStatus(429);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternal_APIエンドポイントでレート制限超過時はJSONを返す() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/training/today");
    when(request.getMethod()).thenReturn("GET");
    when(bucketManager.apiReadBucket(any())).thenReturn(bucket);
    when(bucket.tryConsume(1)).thenReturn(false);
    when(response.getWriter()).thenReturn(mock(PrintWriter.class));

    filter.doFilterInternal(request, response, chain);

    verify(response).setStatus(429);
    verify(response, never()).sendRedirect(any());
  }
}
