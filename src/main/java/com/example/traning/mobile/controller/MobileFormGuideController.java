package com.example.traning.mobile.controller;

import com.example.traning.dao.TrainingItemFormGuideDao;
import com.example.traning.entity.TrainingItemFormCaution;
import com.example.traning.entity.TrainingItemFormGuide;
import com.example.traning.mobile.dto.FormGuideResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 種目のフォーム解説（画像/動画・注意事項）取得API。機能見直し-1-#2。
 *
 * <p>フォーム解説はユーザー自身のデータではなく全ユーザー共通の運営コンテンツのため、所有者チェックは
 * 不要（認証済みであれば取得可能、mobileSecurityFilterChainの{@code anyRequest().authenticated()}のみ）。
 */
@RestController
@RequestMapping("/api/mobile/form-guides")
public class MobileFormGuideController {

  private final TrainingItemFormGuideDao formGuideDao;

  public MobileFormGuideController(TrainingItemFormGuideDao formGuideDao) {
    this.formGuideDao = formGuideDao;
  }

  /** 種目名を指定してフォーム解説を取得する。対象8種目以外がタップされることはUI上想定していないが、 防御的に404を返す。 */
  @GetMapping("/{itemName}")
  public ResponseEntity<FormGuideResponse> get(@PathVariable String itemName) {
    Optional<TrainingItemFormGuide> guide = formGuideDao.selectByItemName(itemName);
    if (guide.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    List<TrainingItemFormCaution> cautions = formGuideDao.selectCautionsByItemName(itemName);
    return ResponseEntity.ok(FormGuideResponse.from(guide.get(), cautions));
  }
}
