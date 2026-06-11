package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dao.MobileDeviceTokenDao;
import com.example.traning.mobile.dto.PushRegisterRequest;
import com.example.traning.mobile.entity.MobileDeviceToken;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/push")
public class MobilePushController {

  private final MobileDeviceTokenDao deviceTokenDao;

  public MobilePushController(MobileDeviceTokenDao deviceTokenDao) {
    this.deviceTokenDao = deviceTokenDao;
  }

  /** FCMデバイストークンを登録（同一デバイスIDの既存レコードがあれば更新）。 ログイン直後またはトークンが再発行された際に呼び出す。 */
  @PostMapping("/register")
  @Transactional
  @AuditLog(action = "MOBILE_PUSH_REGISTER", targetTable = "mobile_device_tokens")
  public ResponseEntity<Void> register(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody PushRegisterRequest req) {

    Optional<MobileDeviceToken> existing =
        deviceTokenDao.selectByUserIdAndDeviceId(userId, req.getDeviceId());

    if (existing.isPresent()) {
      MobileDeviceToken token = existing.get();
      token.setDeviceToken(req.getFcmToken());
      token.setPlatform(req.getPlatform());
      token.setIsActive(true);
      token.setUpdatedAt(LocalDateTime.now());
      deviceTokenDao.update(token);
    } else {
      MobileDeviceToken token = new MobileDeviceToken();
      token.setUserId(userId);
      token.setDeviceToken(req.getFcmToken());
      token.setPlatform(req.getPlatform());
      token.setDeviceId(req.getDeviceId());
      token.setIsActive(true);
      deviceTokenDao.insert(token);
    }

    return ResponseEntity.noContent().build();
  }

  /** FCMデバイストークンを削除する（ログアウト時に呼び出す）。 */
  @DeleteMapping("/unregister")
  @Transactional
  @AuditLog(action = "MOBILE_PUSH_UNREGISTER", targetTable = "mobile_device_tokens")
  public ResponseEntity<Void> unregister(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody PushRegisterRequest req) {

    deviceTokenDao.deleteByUserIdAndDeviceId(userId, req.getDeviceId());
    return ResponseEntity.noContent().build();
  }
}
