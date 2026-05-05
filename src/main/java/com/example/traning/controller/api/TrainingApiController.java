package com.example.traning.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.training.entity.TrainingDetail;
import com.example.traning.training.service.TrainingService;

@RestController
public class TrainingApiController {

    @Autowired
    private TrainingService trainingService;

    @GetMapping("/admin/api/training-details")
    public List<TrainingDetail> getDetails(@RequestParam String date) {
        // Service経由でDomaのDaoを叩き、その日の記録を取得
        // dateは "2026-05-04" のような文字列で届くので、必要に応じてLocalDateに変換
        return trainingService.findByDate(date);
    }

    @GetMapping("/admin/api/training-volume/{userId}")
    @ResponseBody
    public Map<String, Object> getTrainingVolume(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        // Service側も startDate, endDate を受け取るように変更
        return trainingService.makeChartDataCustom(userId, startDate, endDate);
    }
}