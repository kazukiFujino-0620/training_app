package com.example.traning.smarttrainer.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.smarttrainer.task.MasterUpdateTask;
import com.example.traning.smarttrainer.task.MasterUpdateTask.MasterUpdateResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * トレーニングマスタ（種目マスタ）のCSV取り込み・登録を管理者が行うための画面。
 *
 * <p>既存の夜間バッチ（{@link MasterUpdateTask}）はそのまま残し、本コントローラーは (1) 取り込み用CSVファイルのアップロード、(2) バッチの即時手動起動、(3)
 * 現在のマスタデータのCSVダウンロード、 の3操作を管理者向けに提供する。
 */
@Controller
@RequestMapping("/admin/master")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminMasterController {

  private final MasterUpdateTask masterUpdateTask;
  private final TrainingMasterDao trainingMasterDao;

  @Value("${batch.master.update.file-path}")
  private String filePath;

  public AdminMasterController(
      MasterUpdateTask masterUpdateTask, TrainingMasterDao trainingMasterDao) {
    this.masterUpdateTask = masterUpdateTask;
    this.trainingMasterDao = trainingMasterDao;
  }

  @GetMapping
  public String index(Model model) {
    model.addAttribute("itemCount", trainingMasterDao.selectAllItems().size());
    model.addAttribute("filePath", filePath);
    return "admin/master_management";
  }

  /**
   * CSVファイルをアップロードし、バッチが読み込む固定パスへ上書き保存する。 アップロードされたファイルの元のファイル名はパスに一切使用しない（固定パスへの上書きのみ）ため、
   * パストラバーサルの余地を作らない。
   */
  @AuditLog(action = "ADMIN_MASTER_CSV_UPLOAD", targetTable = "training_item_master")
  @PostMapping("/upload")
  public String upload(
      @RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
    if (file.isEmpty()) {
      redirectAttributes.addFlashAttribute("errorMessage", "ファイルを選択してください。");
      return "redirect:/admin/master";
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
      redirectAttributes.addFlashAttribute("errorMessage", "CSVファイル（.csv）を選択してください。");
      return "redirect:/admin/master";
    }

    try {
      Path destination = new File(filePath).toPath();
      Files.createDirectories(destination.getParent());
      file.transferTo(destination.toFile());
      log.info("マスタ更新用CSVファイルをアップロードしました: {} ({} bytes)", destination, file.getSize());
      redirectAttributes.addFlashAttribute(
          "successMessage", "CSVファイルをアップロードしました。反映するには「今すぐ取り込む」を実行するか、次回の夜間バッチをお待ちください。");
    } catch (IOException e) {
      log.error("CSVファイルのアップロードに失敗しました", e);
      redirectAttributes.addFlashAttribute("errorMessage", "アップロードに失敗しました: " + e.getMessage());
    }

    return "redirect:/admin/master";
  }

  /** 夜間バッチと同じロジックを、管理者の操作で即時実行する。 */
  @AuditLog(action = "ADMIN_MASTER_CSV_EXECUTE", targetTable = "training_item_master")
  @PostMapping("/execute")
  public String execute(RedirectAttributes redirectAttributes) {
    MasterUpdateResult result = masterUpdateTask.executeMasterUpdate();
    redirectAttributes.addFlashAttribute(
        result.success() ? "successMessage" : "errorMessage", result.message());
    return "redirect:/admin/master";
  }

  /** 現在のマスタデータを、取り込み用CSVと同じ形式（part_code,item_name,master_flg）でダウンロードする。 */
  @AuditLog(action = "ADMIN_MASTER_CSV_EXPORT", targetTable = "training_item_master")
  @GetMapping("/export/csv")
  public void exportCsv(HttpServletResponse response) throws IOException {
    List<TrainingItemMaster> items = trainingMasterDao.selectAllItems();

    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"master_data.csv\"");

    // UTF-8 BOM（Excel での文字化け防止。DataExportService と同じ方式）
    response.getOutputStream().write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

    CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator("\r\n").build();
    try (CSVPrinter printer =
        new CSVPrinter(
            new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8), format)) {
      printer.printRecord("part_code", "item_name", "master_flg");
      for (TrainingItemMaster item : items) {
        printer.printRecord(
            item.getPartCode(),
            item.getItemName(),
            item.getMasterFlg() != null ? item.getMasterFlg() : 1);
      }
    }
  }
}
