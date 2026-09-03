package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.UserDao;
import com.example.traning.mobile.dto.WithdrawalRequestDto;
import com.example.traning.mobile.dto.WithdrawalStatusResponse;
import com.example.traning.user.User;
import com.example.traning.withdrawal.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイル: 退会画面API（ita3-3関連）。
 *
 * <p>ジム所属ユーザーはWeb版と同じ申請制（管理者承認後に削除）、招待コードなし登録の一般ユーザーは 申請を挟まず即時削除となる。どちらのフローを使うかは{@link
 * #status}が返す{@code isGeneralUser}で判定する。
 */
@RestController
@RequestMapping("/api/mobile/withdrawal")
public class MobileWithdrawalController {

  private final WithdrawalService withdrawalService;
  private final UserDao userDao;

  public MobileWithdrawalController(WithdrawalService withdrawalService, UserDao userDao) {
    this.withdrawalService = withdrawalService;
    this.userDao = userDao;
  }

  @GetMapping("/status")
  public ResponseEntity<WithdrawalStatusResponse> status(@AuthenticationPrincipal Long userId) {
    User user = userDao.selectById(userId.intValue());
    boolean isGeneralUser = withdrawalService.isGeneralUser(user);
    boolean hasPendingRequest = !isGeneralUser && withdrawalService.hasPendingRequest(userId);
    return ResponseEntity.ok(new WithdrawalStatusResponse(isGeneralUser, hasPendingRequest));
  }

  /** ジム所属ユーザー向け：退会申請（管理者承認待ちになる）。 */
  @AuditLog(action = "MOBILE_WITHDRAWAL_REQUEST", targetTable = "withdrawal_requests")
  @PostMapping("/request")
  public ResponseEntity<Void> request(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody WithdrawalRequestDto body) {
    withdrawalService.createRequest(userId, body.getReasonType(), body.getReasonText());
    return ResponseEntity.noContent().build();
  }

  /** ジム所属ユーザー向け：申請中の退会申請をキャンセルする。 */
  @AuditLog(action = "MOBILE_WITHDRAWAL_CANCEL", targetTable = "withdrawal_requests")
  @PostMapping("/cancel")
  public ResponseEntity<Void> cancel(@AuthenticationPrincipal Long userId) {
    withdrawalService.cancelRequest(userId);
    return ResponseEntity.noContent().build();
  }

  /** 一般ユーザー向け：申請を挟まず即座にアカウント・データを削除する。 */
  @AuditLog(action = "MOBILE_WITHDRAWAL_DELETE_IMMEDIATELY", targetTable = "users")
  @PostMapping("/delete-immediately")
  public ResponseEntity<Void> deleteImmediately(@AuthenticationPrincipal Long userId) {
    withdrawalService.selfDeleteImmediately(userId);
    return ResponseEntity.noContent().build();
  }
}
