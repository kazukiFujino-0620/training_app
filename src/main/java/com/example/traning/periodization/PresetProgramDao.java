package com.example.traning.periodization;

import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

/** 既定プログラム（プリセット）マスタの参照DAO（機能見直し-1-#3）。書き込みはマイグレーションでのみ行う。 */
@Dao
@ConfigAutowireable
public interface PresetProgramDao {

  /** 指定した組織ID群（0=全組織共通を含めて呼び出し側で渡す）に属するプリセット一覧。 */
  @Select
  List<PresetProgram> selectByOrganizationIds(List<Long> organizationIds);

  @Select
  Optional<PresetProgram> selectById(Long id);

  @Select
  List<PresetProgramWeek> selectWeeksByPresetId(Long presetProgramId);

  @Select
  List<PresetProgramDayTemplate> selectDayTemplatesByPresetId(Long presetProgramId);

  /** プリセット配下の全曜日テンプレートにぶら下がる種目（day_template_id・display_order順）。 */
  @Select
  List<PresetProgramDayTemplateItem> selectItemsByPresetId(Long presetProgramId);
}
