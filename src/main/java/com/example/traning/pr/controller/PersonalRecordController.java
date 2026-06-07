package com.example.traning.pr.controller;

import com.example.traning.goal.GoalService;
import com.example.traning.goal.GoalWithProgress;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.service.TrainingService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class PersonalRecordController {

  private final PersonalRecordService personalRecordService;
  private final TrainingService trainingService;
  private final GoalService goalService;

  @GetMapping("/pr")
  public String prList(Model model, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<PersonalRecord> records = personalRecordService.getByUserId(userId);

    // ACTIVE 目標を itemName → GoalWithProgress の Map に変換
    List<GoalWithProgress> allGoals = goalService.listGoalsWithProgress(userId);
    Map<String, GoalWithProgress> goalMap =
        allGoals.stream()
            .filter(g -> "ACTIVE".equals(g.effectiveStatus()))
            .collect(Collectors.toMap(g -> g.goal().getItemName(), g -> g, (a, b) -> a));

    // PR が存在しない種目の ACTIVE 目標（テーブル下部に別セクション表示）
    Set<String> prItemNames =
        records.stream().map(PersonalRecord::getItemName).collect(Collectors.toSet());
    List<GoalWithProgress> goalsWithoutPr =
        allGoals.stream()
            .filter(
                g ->
                    "ACTIVE".equals(g.effectiveStatus())
                        && !prItemNames.contains(g.goal().getItemName()))
            .toList();

    model.addAttribute("records", records);
    model.addAttribute("goalMap", goalMap);
    model.addAttribute("goalsWithoutPr", goalsWithoutPr);
    return "pr";
  }

  @GetMapping("/api/pr")
  @ResponseBody
  public List<PersonalRecord> prListApi(Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    return personalRecordService.getByUserId(userId);
  }
}
