package com.example.traning.mobile.controller;

import com.example.traning.dao.TrainingItemFormGuideDao;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.mobile.dto.TrainingItemMasterResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/master")
public class MobileMasterController {

  private final TrainingMasterDao trainingMasterDao;
  private final TrainingItemFormGuideDao formGuideDao;

  public MobileMasterController(
      TrainingMasterDao trainingMasterDao, TrainingItemFormGuideDao formGuideDao) {
    this.trainingMasterDao = trainingMasterDao;
    this.formGuideDao = formGuideDao;
  }

  /**
   * 種目マスタ一覧を返す。 partCode を指定するとその部位の種目のみ返す。
   *
   * <p>本エンドポイントは新規トレーニング登録画面（AddExerciseScreen）専用のため、常にmaster_flg=1（使用可能）の種目のみ返す。
   *
   * <p>機能見直し-1-#2: 各種目にフォーム解説（training_item_form_guides）が登録済みかどうかを {@code hasFormGuide}
   * として一括付与する。種目数だけ個別APIを呼ぶN+1を避けるため、 対象種目名の集合を1クエリで取得してからマージする。
   */
  @GetMapping("/items")
  public ResponseEntity<List<TrainingItemMasterResponse>> getItems(
      @RequestParam(required = false) String partCode) {

    List<TrainingItemMaster> items =
        (partCode != null && !partCode.isBlank())
            ? trainingMasterDao.selectActiveItemsByPart(partCode)
            : trainingMasterDao.selectActiveItems();

    Set<String> guidedItemNames = new HashSet<>(formGuideDao.selectAllItemNamesWithGuide());
    List<TrainingItemMasterResponse> response =
        items.stream()
            .map(
                item ->
                    TrainingItemMasterResponse.from(
                        item, guidedItemNames.contains(item.getItemName())))
            .toList();

    return ResponseEntity.ok(response);
  }
}
