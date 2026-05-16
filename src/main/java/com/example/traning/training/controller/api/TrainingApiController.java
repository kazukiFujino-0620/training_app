package com.example.traning.training.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.training.TrainingDetail;
import com.example.traning.training.service.TrainingService;

import lombok.extern.slf4j.Slf4j;

/**
 * トレーニング関連 REST API コントローラー。
 */
@RestController
@Slf4j
public class TrainingApiController {

    private final TrainingService trainingService;

    public TrainingApiController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * 指定ユーザー・日付のトレーニング詳細を取得する（管理者用）。
     *
     * 以前は SecurityConfig の URL マッチングのみに依存しており、
     * 設定の変更や別コントローラーへの移動で認可が外れるリスクがあった。
     */
    @GetMapping("/admin/api/training-details")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TrainingDetail> getDetails(
            @RequestParam String date,
            @RequestParam(required = false) Long userId) {

        log.info("管理者: トレーニング詳細取得 userId={}, date={}", userId, date);

        if (userId != null) {
            return trainingService.findByUserIdAndDate(userId, date);
        }
        return trainingService.findByDate(date);
    }

    /**
     * 指定ユーザーのトレーニングボリューム（部位別・期間別）を取得する（管理者用）。
     *
     * userId をパスパラメータで受け取るため、一般ユーザーが他ユーザーの
     * データへアクセスできる IDOR 脆弱性を URLレベル + メソッドレベルの
     * 二重チェックで防止する。
     */
    @GetMapping("/admin/api/training-volume/{userId}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getTrainingVolume(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        log.info("管理者: ボリュームデータ取得 userId={}, {} ~ {}", userId, startDate, endDate);
        return trainingService.makeChartDataCustom(userId, startDate, endDate);
    }
}