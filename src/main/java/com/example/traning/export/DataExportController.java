package com.example.traning.export;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/user/export")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportService dataExportService;
    private final UserService userService;

    @GetMapping
    public String showExportForm() {
        return "user/export";
    }

    @GetMapping("/csv")
    public void downloadCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal,
            HttpServletResponse response) throws IOException {

        if (from.isAfter(to)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "開始日は終了日以前の日付を指定してください");
            return;
        }

        User loginUser = userService.getUserByEmail(principal.getName());
        Long userId = loginUser.getUserId().longValue();
        String filename = String.format("training_data_%d_%s_%s.csv", userId, from, to);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        dataExportService.writeCsv(userId, from, to, response.getOutputStream());
    }
}
