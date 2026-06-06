package com.example.traning.export;

import com.example.traning.body.BodyMeasurement;
import com.example.traning.dao.UserDao;
import com.example.traning.goal.GoalDao;
import com.example.traning.goal.TrainingGoal;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataExportService {

  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final GoalDao goalDao;
  private final UserDao userDao;

  @Transactional(readOnly = true)
  public void writeCsv(
      Long userId,
      LocalDate from,
      LocalDate to,
      List<BodyMeasurement> measurements,
      OutputStream out)
      throws IOException {
    User user = userDao.selectById(userId.intValue());
    List<Training> trainings = trainingDao.selectByDate(userId, from, to);
    List<TrainingGoal> goals = goalDao.selectByUserId(userId);

    // UTF-8 BOM（Excel での文字化け防止）
    out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

    CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator("\r\n").build();

    OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    try (CSVPrinter printer = new CSVPrinter(writer, format)) {
      writeProfileSection(printer, user);
      writeTrainingSection(printer, trainings, from, to);
      writeGoalSection(printer, goals);
      writeBodySection(printer, measurements);
    }
  }

  private void writeProfileSection(CSVPrinter printer, User user) throws IOException {
    printer.printRecord("## プロフィール");
    printer.printRecord("ユーザー名", "身長(cm)", "体重(kg)", "生年月日", "性別");
    printer.printRecord(
        user.getUserName(),
        user.getHeightCm(),
        user.getWeightKg(),
        user.getBirthDate(),
        user.getGender());
    printer.println();
  }

  private void writeTrainingSection(
      CSVPrinter printer, List<Training> trainings, LocalDate from, LocalDate to)
      throws IOException {
    printer.printRecord("## トレーニング記録（" + from + " 〜 " + to + "）");
    printer.printRecord("日付", "部位", "種目", "セット番号", "セット種別", "重量(kg)", "回数", "完了", "メモ");

    for (Training t : trainings) {
      String partLabel = resolvePartLabel(t.getPartCode());
      List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(t.getId());
      for (TrainingDetail d : details) {
        printer.printRecord(
            t.getTrainingDate(),
            partLabel,
            t.getMenu(),
            d.getSetNumber(),
            resolveSetTypeLabel(d.getSetType()),
            d.getWeight(),
            d.getReps(),
            d.getIsCompleted() ? "✓" : "",
            t.getMemo());
      }
    }
    printer.println();
  }

  private void writeGoalSection(CSVPrinter printer, List<TrainingGoal> goals) throws IOException {
    printer.printRecord("## 目標設定");
    printer.printRecord("種目名", "目標重量(kg)", "目標回数", "目標日", "ステータス");
    for (TrainingGoal g : goals) {
      printer.printRecord(
          g.getItemName(),
          g.getTargetWeight(),
          g.getTargetReps(),
          g.getTargetDate(),
          g.getStatus());
    }
  }

  private void writeBodySection(CSVPrinter printer, List<BodyMeasurement> measurements)
      throws IOException {
    printer.printRecord("## 体重記録");
    printer.printRecord("日付", "体重(kg)", "体脂肪率(%)", "メモ");
    for (BodyMeasurement m : measurements) {
      printer.printRecord(
          m.measuredDate,
          m.weightKg,
          m.bodyFatPct != null ? m.bodyFatPct : "",
          m.memo != null ? m.memo : "");
    }
    printer.println();
  }

  private String resolvePartLabel(String partCode) {
    if (partCode == null) return "";
    return switch (partCode) {
      case "CHEST" -> "胸";
      case "BACK" -> "背中";
      case "ARM" -> "腕";
      case "SHOULDER" -> "肩";
      case "LEG" -> "脚";
      default -> partCode;
    };
  }

  private String resolveSetTypeLabel(String setType) {
    if (setType == null) return "";
    return switch (setType) {
      case "WARMUP" -> "ウォームアップ";
      case "MAIN" -> "メイン";
      case "DROP" -> "ドロップ";
      default -> setType;
    };
  }
}
