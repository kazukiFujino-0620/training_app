package com.example.traning.smarttrainer.autogen;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface EquipmentDao {

  /** 全ジム共通（0）とユーザーの所属ジムの器具。 */
  @Select
  List<Equipment> selectByOrganizationIds(List<Long> organizationIds);

  @Select
  List<TrainingItemEquipment> selectAllItemEquipment();
}
