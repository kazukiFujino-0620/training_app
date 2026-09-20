package com.example.traning.dao;

import com.example.traning.entity.TrainingItemFormCaution;
import com.example.traning.entity.TrainingItemFormGuide;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

/**
 * 種目のフォーム解説（画像/動画・注意事項）用Dao。機能見直し-1-#2。
 *
 * <p>運営向け管理画面からの登録・編集は今回スコープ外のため、update/deleteは持たない （初期データはマイグレーション後続の別バージョンで投入する想定。優先度が上がった際に
 * 管理画面用のupdate/delete追加で対応できるよう、テーブル・エンティティは正規化構造にしてある）。
 */
@Dao
@ConfigAutowireable
public interface TrainingItemFormGuideDao {

  @Select
  Optional<TrainingItemFormGuide> selectByItemName(String itemName);

  @Select
  List<TrainingItemFormCaution> selectCautionsByItemName(String itemName);

  /**
   * フォーム解説が登録済みの種目名一覧を返す。AddExerciseScreenの種目一覧に hasFormGuideフラグを一括付与する用途（N+1回避のため1クエリでまとめて判定する）。
   */
  @Select
  List<String> selectAllItemNamesWithGuide();

  @Insert
  int insert(TrainingItemFormGuide entity);

  @Insert
  int insertCaution(TrainingItemFormCaution entity);
}
