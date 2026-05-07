package com.example.traning.training.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.training.TrainingDetail;
import com.example.traning.training.service.TrainingService;

@RestController
public class TrainingApiController {

    private final TrainingService trainingService;

    public TrainingApiController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping("/admin/api/training-details")
    public List<TrainingDetail> getDetails(
            @RequestParam String date,
            @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return trainingService.findByUserIdAndDate(userId, date);
        }
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