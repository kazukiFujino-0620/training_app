package com.example.traning.mobile.controller;

import com.example.traning.smarttrainer.coaching.AiSuggestedDay;
import com.example.traning.smarttrainer.coaching.AiTrainingSuggestionService;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ita5-1 機能1（仮連携）: モバイル向けAIトレーニング提案API。
 *
 * <p>週頭（月曜）に1回7日分をまとめて生成・キャッシュし、本日分のみを返す（反映操作自体は1日分ずつ行う設計のため）。
 * 同意していない場合・提案が無い場合は204を返す（Web版のような同意案内はモバイル側では表示せず、提案が無い時と 同様にカード自体を非表示にする簡易対応。本番実装時に見直し予定）。
 */
@RestController
@RequestMapping("/api/mobile/coaching")
public class MobileCoachingController {

  private final AiTrainingSuggestionService aiTrainingSuggestionService;
  private final UserService userService;

  public MobileCoachingController(
      AiTrainingSuggestionService aiTrainingSuggestionService, UserService userService) {
    this.aiTrainingSuggestionService = aiTrainingSuggestionService;
    this.userService = userService;
  }

  @GetMapping("/training-suggestion/today")
  public ResponseEntity<AiSuggestedDay> getTodayTrainingSuggestion(
      @AuthenticationPrincipal Long userId) {
    User user = userService.getUserById(userId.intValue());
    Optional<AiSuggestedDay> suggestion = aiTrainingSuggestionService.getTodayEntry(user);
    return suggestion.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
  }
}
