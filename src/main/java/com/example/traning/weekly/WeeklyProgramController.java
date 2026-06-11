package com.example.traning.weekly;

import com.example.traning.template.TrainingTemplate;
import com.example.traning.template.TrainingTemplateDao;
import com.example.traning.training.service.TrainingService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/weekly-program")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WeeklyProgramController {

  private final WeeklyProgramService weeklyProgramService;
  private final TrainingService trainingService;
  private final TrainingTemplateDao trainingTemplateDao;

  private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
  private static final String[] DAY_LABELS = {"月", "火", "水", "木", "金", "土", "日"};

  @GetMapping
  public String showForm(Model model, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());

    Map<String, WeeklyProgram> settingsMap = weeklyProgramService.getSettingsMap(userId);
    List<TrainingTemplate> templates = trainingTemplateDao.selectByUserId(userId);

    model.addAttribute("dayCodes", DAY_CODES);
    model.addAttribute("dayLabels", DAY_LABELS);
    model.addAttribute("settingsMap", settingsMap);
    model.addAttribute("templates", templates);
    return "user/weekly_program";
  }

  @PostMapping
  public String save(
      @RequestParam Map<String, String> params,
      Principal principal,
      RedirectAttributes redirectAttributes) {

    Long userId = trainingService.getUserIdByEmail(principal.getName());

    java.util.LinkedHashMap<String, String> dayToPart = new java.util.LinkedHashMap<>();
    java.util.LinkedHashMap<String, Long> dayToTemplate = new java.util.LinkedHashMap<>();

    for (String dayCode : DAY_CODES) {
      String partCode = params.getOrDefault("partCode_" + dayCode, "");
      dayToPart.put(dayCode, partCode);
      String templateIdStr = params.getOrDefault("templateId_" + dayCode, "");
      if (!templateIdStr.isBlank()) {
        try {
          dayToTemplate.put(dayCode, Long.parseLong(templateIdStr));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    weeklyProgramService.saveSettings(userId, dayToPart, dayToTemplate);
    redirectAttributes.addFlashAttribute("successMessage", "週間プログラムを保存しました。");
    return "redirect:/user/weekly-program";
  }
}
