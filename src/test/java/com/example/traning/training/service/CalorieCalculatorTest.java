package com.example.traning.training.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.service.CalorieCalculator.CalorieEstimate;
import com.example.traning.training.service.CalorieCalculator.CalorieType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ita2-2（消費カロリー計算見直し）・ita2-1（有酸素運動除外）のロジック検証。
 *
 * <p>計算式: 仕事量(J) = 重量(kg) × 9.8 × ROM(m) × 回数、消費カロリー(kcal) = 仕事量 ÷ 0.2 ÷ 4184。
 */
class CalorieCalculatorTest {

  private final CalorieCalculator calculator = new CalorieCalculator();

  private static TrainingItemMaster item(String name, String rom) {
    TrainingItemMaster item = new TrainingItemMaster();
    item.setItemName(name);
    item.setRangeOfMotionM(rom == null ? null : new BigDecimal(rom));
    return item;
  }

  private static TrainingDetail detail(String setType, Double weight, Integer reps) {
    TrainingDetail detail = new TrainingDetail();
    detail.setSetType(setType);
    detail.setWeight(weight);
    detail.setReps(reps);
    return detail;
  }

  private static Training training(String partCode, String menu, TrainingDetail... details) {
    Training training = new Training();
    training.setPartCode(partCode);
    training.setMenu(menu);
    training.setDetails(List.of(details));
    return training;
  }

  @Test
  void 単一MAINセットの仕事量から手計算値と一致するカロリーを算出する() {
    // 100kg × 9.8 × 0.45m × 8回 = 3528J → 3528 / 0.2 / 4184 = 4.217... → 4kcal
    Map<String, TrainingItemMaster> itemMaster = Map.of("ベンチプレス", item("ベンチプレス", "0.45"));
    List<Training> trainings = List.of(training("CHEST", "ベンチプレス", detail("MAIN", 100.0, 8)));

    CalorieEstimate result = calculator.estimate(trainings, itemMaster);

    assertThat(result.type).isEqualTo(CalorieType.CALCULATED);
    assertThat(result.calories).isEqualTo(4);
  }

  @Test
  void WARMUPセットはカロリー計算から除外される() {
    Map<String, TrainingItemMaster> itemMaster = Map.of("ベンチプレス", item("ベンチプレス", "0.45"));
    List<Training> withWarmup =
        List.of(
            training(
                "CHEST",
                "ベンチプレス",
                detail("WARMUP", 999.0, 999), // 巨大な値でも無視されるはず
                detail("MAIN", 100.0, 8)));
    List<Training> mainOnly = List.of(training("CHEST", "ベンチプレス", detail("MAIN", 100.0, 8)));

    CalorieEstimate withWarmupResult = calculator.estimate(withWarmup, itemMaster);
    CalorieEstimate mainOnlyResult = calculator.estimate(mainOnly, itemMaster);

    assertThat(withWarmupResult.calories).isEqualTo(mainOnlyResult.calories);
  }

  @Test
  void DROPセットはカロリー計算から除外される() {
    Map<String, TrainingItemMaster> itemMaster = Map.of("スクワット", item("スクワット", "0.40"));
    List<Training> withDrop =
        List.of(training("LEG", "スクワット", detail("MAIN", 100.0, 8), detail("DROP", 500.0, 500)));
    List<Training> mainOnly = List.of(training("LEG", "スクワット", detail("MAIN", 100.0, 8)));

    CalorieEstimate withDropResult = calculator.estimate(withDrop, itemMaster);
    CalorieEstimate mainOnlyResult = calculator.estimate(mainOnly, itemMaster);

    assertThat(withDropResult.calories).isEqualTo(mainOnlyResult.calories);
  }

  @Test
  void 有酸素運動CARDIOは計算対象から除外され筋トレ分のみ合算される() {
    Map<String, TrainingItemMaster> itemMaster = new HashMap<>();
    itemMaster.put("ベンチプレス", item("ベンチプレス", "0.45"));
    itemMaster.put("エアロバイク", item("エアロバイク", "0.40")); // マイグレーションのデフォルト値相当

    List<Training> mixed =
        List.of(
            training("CHEST", "ベンチプレス", detail("MAIN", 100.0, 8)),
            training(
                "CARDIO", "エアロバイク", detail("MAIN", 0.0, 0))); // カーディオはダミーのweight=0/reps=0で登録される実装

    CalorieEstimate mixedResult = calculator.estimate(mixed, itemMaster);
    CalorieEstimate strengthOnlyResult =
        calculator.estimate(
            List.of(training("CHEST", "ベンチプレス", detail("MAIN", 100.0, 8))), itemMaster);

    assertThat(mixedResult.type).isEqualTo(CalorieType.CALCULATED);
    assertThat(mixedResult.calories).isEqualTo(strengthOnlyResult.calories);
  }

  @Test
  void 有酸素運動のみの日は算出不可になる() {
    Map<String, TrainingItemMaster> itemMaster = Map.of("水泳", item("水泳", "0.40"));
    List<Training> cardioOnly = List.of(training("CARDIO", "水泳", detail("MAIN", 0.0, 0)));

    CalorieEstimate result = calculator.estimate(cardioOnly, itemMaster);

    assertThat(result.type).isEqualTo(CalorieType.UNAVAILABLE);
    assertThat(result.calories).isNull();
  }

  @Test
  void トレーニングが空の場合は算出不可になる() {
    CalorieEstimate result = calculator.estimate(List.of(), Map.of());
    assertThat(result.type).isEqualTo(CalorieType.UNAVAILABLE);
  }

  @Test
  void trainingsがnullの場合は算出不可になる() {
    CalorieEstimate result = calculator.estimate(null, Map.of());
    assertThat(result.type).isEqualTo(CalorieType.UNAVAILABLE);
  }

  @Test
  void 種目マスタに存在しない種目は算出不可になる() {
    List<Training> trainings = List.of(training("CHEST", "未登録種目", detail("MAIN", 100.0, 8)));
    CalorieEstimate result = calculator.estimate(trainings, Map.of());
    assertThat(result.type).isEqualTo(CalorieType.UNAVAILABLE);
  }

  @Test
  void ROMが未設定の種目は算出不可になる() {
    Map<String, TrainingItemMaster> itemMaster = Map.of("謎の種目", item("謎の種目", null));
    List<Training> trainings = List.of(training("CHEST", "謎の種目", detail("MAIN", 100.0, 8)));

    CalorieEstimate result = calculator.estimate(trainings, itemMaster);

    assertThat(result.type).isEqualTo(CalorieType.UNAVAILABLE);
  }

  @Test
  void 複数種目の仕事量が正しく合算される() {
    Map<String, TrainingItemMaster> itemMaster = new HashMap<>();
    itemMaster.put("ベンチプレス", item("ベンチプレス", "0.45"));
    itemMaster.put("スクワット", item("スクワット", "0.60"));

    // ベンチ: 100 x 9.8 x 0.45 x 8 = 3528J
    // スクワット: 80 x 9.8 x 0.60 x 10 = 4704J
    // 合計: 8232J / 0.2 / 4184 = 9.837... -> 10kcal
    List<Training> trainings =
        List.of(
            training("CHEST", "ベンチプレス", detail("MAIN", 100.0, 8)),
            training("LEG", "スクワット", detail("MAIN", 80.0, 10)));

    CalorieEstimate result = calculator.estimate(trainings, itemMaster);

    assertThat(result.type).isEqualTo(CalorieType.CALCULATED);
    assertThat(result.calories).isEqualTo(10);
  }
}
