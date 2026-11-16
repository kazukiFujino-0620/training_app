package com.example.traning.user.service;

import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * itバグ-48: 役割（権限）が変わったユーザーのログイン状態を無効にする。
 *
 * <p>Webのセッションは {@link SessionRegistry} 上で期限切れにする。次のリクエストで {@code ConcurrentSessionFilter}
 * がログアウト処理（Remember-meクッキーの削除を含む）を行い、ログイン画面へ戻す。Remember-meクッキーはDBのユーザー情報から
 * 権限を読み直すため、別端末に残ったクッキーで再ログインしても新しい役割になる。
 *
 * <p>モバイルはリフレッシュトークンを全端末分削除する。アクセストークンは {@code JwtAuthenticationFilter}
 * がDBの役割と照合し、変更前に発行されたもの（役割が一致しないもの）を拒否する。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserSessionInvalidator {

  private final SessionRegistry sessionRegistry;
  private final MobileRefreshTokenDao mobileRefreshTokenDao;

  /**
   * 対象ユーザーのWebセッションとモバイルのリフレッシュトークンを無効にする。
   *
   * <p>トランザクション内で呼ばれた場合、セッションの期限切れはコミット後に行う（コミット前に再ログインされて 変更前の役割を読み込むことを防ぐ）。
   */
  public void invalidateAll(User user) {
    mobileRefreshTokenDao.deleteByUserId(user.getUserId().longValue());
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              expireWebSessions(user);
            }
          });
    } else {
      expireWebSessions(user);
    }
  }

  private void expireWebSessions(User user) {
    int expired = 0;
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      // フォームログイン（Spring Securityの User）・Google/LINEログイン（CustomUserDetails）ともに username はメールアドレス
      if (principal instanceof UserDetails details
          && details.getUsername() != null
          && details.getUsername().equalsIgnoreCase(user.getEmail())) {
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
        sessions.forEach(SessionInformation::expireNow);
        expired += sessions.size();
      }
    }
    log.info("役割の変更によりログイン状態を無効化しました userId={} sessions={}", user.getUserId(), expired);
  }
}
