package com.example.traning.mobile.controller;

import com.example.traning.dao.UserDao;
import com.example.traning.notice.Notice;
import com.example.traning.notice.NoticeService;
import com.example.traning.user.User;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** モバイル版お知らせ表示（ita2-5、Web版NoticeControllerのモバイル対応版）。配信・管理はWeb版管理画面のみで行う。 */
@RestController
@RequestMapping("/api/mobile/notices")
public class MobileNoticeController {

  private final NoticeService noticeService;
  private final UserDao userDao;

  public MobileNoticeController(NoticeService noticeService, UserDao userDao) {
    this.noticeService = noticeService;
    this.userDao = userDao;
  }

  /** 未閲覧（未dismiss）のお知らせを新しい順に返す。 */
  @GetMapping("/active")
  public ResponseEntity<List<Notice>> getActive(@AuthenticationPrincipal Long userId) {
    User user = userDao.selectById(userId.intValue());
    return ResponseEntity.ok(noticeService.getActiveForUser(user));
  }

  /** 閲覧済みとして以後表示しないようにする。 */
  @PostMapping("/{id}/dismiss")
  @Transactional
  public ResponseEntity<Void> dismiss(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    noticeService.dismiss(id, userId);
    return ResponseEntity.ok().build();
  }
}
