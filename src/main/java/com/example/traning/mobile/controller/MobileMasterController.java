package com.example.traning.mobile.controller;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/master")
public class MobileMasterController {

  private final TrainingMasterDao trainingMasterDao;

  public MobileMasterController(TrainingMasterDao trainingMasterDao) {
    this.trainingMasterDao = trainingMasterDao;
  }

  /**
   * 種目マスタ一覧を返す。 partCode を指定するとその部位の種目のみ返す。
   *
   * <p>本エンドポイントは新規トレーニング登録画面（AddExerciseScreen）専用のため、常にmaster_flg=1（使用可能）の種目のみ返す。
   */
  @GetMapping("/items")
  public ResponseEntity<List<TrainingItemMaster>> getItems(
      @RequestParam(required = false) String partCode) {

    List<TrainingItemMaster> items =
        (partCode != null && !partCode.isBlank())
            ? trainingMasterDao.selectActiveItemsByPart(partCode)
            : trainingMasterDao.selectActiveItems();

    return ResponseEntity.ok(items);
  }
}
