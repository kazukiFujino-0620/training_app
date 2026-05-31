package com.example.traning.pr.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.service.TrainingService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PersonalRecordController {

    private final PersonalRecordService personalRecordService;
    private final TrainingService trainingService;

    @GetMapping("/pr")
    public String prList(Model model, Principal principal) {
        Long userId = trainingService.getUserIdByEmail(principal.getName());
        List<PersonalRecord> records = personalRecordService.getByUserId(userId);
        model.addAttribute("records", records);
        return "pr";
    }

    @GetMapping("/api/pr")
    @ResponseBody
    public List<PersonalRecord> prListApi(Principal principal) {
        Long userId = trainingService.getUserIdByEmail(principal.getName());
        return personalRecordService.getByUserId(userId);
    }
}
