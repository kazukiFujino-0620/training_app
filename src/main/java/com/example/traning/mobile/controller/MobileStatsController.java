package com.example.traning.mobile.controller;

import com.example.traning.mobile.dto.MobileTrainingStatsResponse;
import com.example.traning.training.service.TrainingStatsService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイルのカレンダータブ下に表示する統計バー用API（ita7-2）。
 *
 * <p>Web版 {@code MenuController.menu()} 内にあった統計算出ロジックを {@link TrainingStatsService}
 * へ切り出し、Web・モバイル双方から共用している。Web用API（{@code /api/**}）とは別Controller・別DTOとする （CLAUDE.md方針）。
 */
@RestController
@RequestMapping("/api/mobile/stats")
@PreAuthorize("isAuthenticated()")
public class MobileStatsController {

  private final TrainingStatsService trainingStatsService;

  public MobileStatsController(TrainingStatsService trainingStatsService) {
    this.trainingStatsService = trainingStatsService;
  }

  /** カレンダータブ下の統計バー（今月・先週比・今週の部位・今日の予定）と、カレンダー上の実施日ドット表示用の当月実施日一覧を返す（ita7-3）。 */
  @GetMapping("/training")
  public ResponseEntity<MobileTrainingStatsResponse> getTrainingStats(
      @AuthenticationPrincipal Long userId) {
    LocalDate today = LocalDate.now();
    TrainingStatsService.TrainingStats stats = trainingStatsService.getStats(userId, today);

    List<MobileTrainingStatsResponse.PartCoverage> weekParts =
        stats.weekParts().stream()
            .map(pc -> new MobileTrainingStatsResponse.PartCoverage(pc.name(), pc.done()))
            .toList();

    List<String> trainingDates =
        trainingStatsService.getTrainingDatesInMonth(userId, YearMonth.from(today)).stream()
            .map(LocalDate::toString)
            .toList();

    MobileTrainingStatsResponse response =
        new MobileTrainingStatsResponse(
            stats.monthlyCount(),
            stats.volumeChangeText(),
            stats.volumeChangePositive(),
            weekParts,
            stats.todayPartLabel(),
            trainingDates);
    return ResponseEntity.ok(response);
  }
}
