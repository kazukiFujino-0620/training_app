package com.example.traning.organization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** ジムごとの設定（機能見直し-1-#15 Q15-2）。行が無いジムは全項目が初期値として扱う（{@link GymSettingService}）。 */
@Entity
@Table(name = "gym_settings")
@Data
public class GymSetting {

  /** organizations.id（type=GYM）。 */
  @Id
  @Column(name = "organization_id")
  private Long organizationId;

  /** 店舗管理者・ジム管理者が会員の健康に関わる情報を見られるか。初期値は見られない。 */
  @Column(name = "staff_health_info_visible")
  private Boolean staffHealthInfoVisible = false;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
