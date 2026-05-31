package com.example.traning.goal;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;

@Controller
@RequestMapping("/user/goals")
public class GoalController {

    private final GoalService goalService;
    private final UserService userService;
    private final TrainingMasterDao trainingMasterDao;

    public GoalController(GoalService goalService, UserService userService,
                          TrainingMasterDao trainingMasterDao) {
        this.goalService = goalService;
        this.userService = userService;
        this.trainingMasterDao = trainingMasterDao;
    }

    @GetMapping
    public String index(Model model, Principal principal) {
        User loginUser = userService.getUserByEmail(principal.getName());
        List<GoalWithProgress> goals = goalService.listGoalsWithProgress(loginUser.getUserId().longValue());

        List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
        Map<String, List<TrainingMaster>> itemsByPart = trainingMasterDao.selectAll().stream()
                .collect(Collectors.groupingBy(TrainingMaster::getPartCode));

        model.addAttribute("loginUser", loginUser);
        model.addAttribute("goals", goals);
        model.addAttribute("goalForm", new GoalForm());
        model.addAttribute("parts", parts);
        model.addAttribute("itemsByPart", itemsByPart);
        return "user/goals";
    }

    @PostMapping
    public String create(@Valid GoalForm goalForm, BindingResult result,
                         Model model, Principal principal,
                         RedirectAttributes redirectAttributes) {
        User loginUser = userService.getUserByEmail(principal.getName());

        boolean targetMissing = goalForm.getTargetWeight() == null && goalForm.getTargetReps() == null;
        if (result.hasErrors() || targetMissing) {
            if (targetMissing) {
                result.reject("goal.target.required", "目標重量または目標回数のどちらかを入力してください");
            }
            List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
            Map<String, List<TrainingMaster>> itemsByPart = trainingMasterDao.selectAll().stream()
                    .collect(Collectors.groupingBy(TrainingMaster::getPartCode));
            model.addAttribute("loginUser", loginUser);
            model.addAttribute("goals", goalService.listGoalsWithProgress(loginUser.getUserId().longValue()));
            model.addAttribute("parts", parts);
            model.addAttribute("itemsByPart", itemsByPart);
            return "user/goals";
        }

        goalService.createGoal(loginUser.getUserId().longValue(), goalForm);
        redirectAttributes.addFlashAttribute("successMessage", "目標を登録しました");
        return "redirect:/user/goals";
    }

    @PostMapping("/{id}/achieve")
    public ResponseEntity<Map<String, String>> achieve(@PathVariable Long id, Principal principal) {
        User loginUser = userService.getUserByEmail(principal.getName());
        goalService.achieveGoal(id, loginUser.getUserId().longValue());
        return ResponseEntity.ok(Map.of("status", "ACHIEVED"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, Principal principal) {
        User loginUser = userService.getUserByEmail(principal.getName());
        goalService.deleteGoal(id, loginUser.getUserId().longValue());
        return ResponseEntity.ok(Map.of("result", "deleted"));
    }
}
