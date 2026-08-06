package com.example.traning.dao;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import java.util.List;
import org.seasar.doma.BatchInsert;
import org.seasar.doma.BatchUpdate;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TrainingMasterDao {
  @Insert
  int insert(TrainingMaster trainingMaster);

  @Select
  List<TrainingMaster> selectAllParts();

  @Select
  List<TrainingItemMaster> selectItemsByPart(String partCode);

  @Select
  String selectNameByCode(String partCode);

  /** 新規種目のみを対象とする（既存行がある場合、ユニーク制約違反になるため呼び出し側で新規分のみに絞ること）。 */
  @BatchInsert
  int[] batchUpsert(List<TrainingItemMaster> entities);

  /** 既存種目のmaster_flgのみを更新する（idで対象を特定）。CSV取り込みでフラグに変更があった既存種目に使用する。 */
  @BatchUpdate
  int[] batchUpdateMasterFlg(List<TrainingItemMaster> entities);

  @Select
  List<TrainingMaster> selectAll();

  @Select
  List<TrainingItemMaster> selectAllItems();

  /** master_flg=1（使用可能）の種目のみを返す。本日以降の新規トレーニング登録の種目選択で使用する。 */
  @Select
  List<TrainingItemMaster> selectActiveItemsByPart(String partCode);

  /** master_flg=1（使用可能）の種目のみを返す（全部位）。本日以降の新規トレーニング登録の種目選択で使用する。 */
  @Select
  List<TrainingItemMaster> selectActiveItems();
}
