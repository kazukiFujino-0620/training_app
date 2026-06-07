package com.example.traning.retention;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class DataRetentionService {

  private final DataRetentionProperties properties;

  public DataRetentionService(DataRetentionProperties properties) {
    this.properties = properties;
  }

  /** バッチ物理削除の基準日時を返す。 create_datetime がこの日時より前のレコードが削除対象。 */
  public LocalDateTime getRetentionCutoff() {
    return LocalDateTime.now().minusYears(properties.getRetentionYears());
  }

  /**
   * 物理削除が許可されるかチェックする。 ADMIN ロールは常に許可。一般ユーザーは保護期間中は不可。
   *
   * @throws RetentionPolicyException 保護期間内かつ非 ADMIN の場合
   */
  public void assertCanPhysicalDelete(String role, LocalDateTime createDatetime) {
    if ("ROLE_ADMIN".equals(role)) {
      return;
    }
    if (!isExpired(createDatetime)) {
      throw new RetentionPolicyException(
          "このデータは保護期間中のため物理削除できません。保護期間: " + properties.getRetentionYears() + "年");
    }
  }

  /** 保護期間が満了しているかどうかを返す（バッチ判定用）。 */
  public boolean isExpired(LocalDateTime createDatetime) {
    return LocalDateTime.now().isAfter(createDatetime.plusYears(properties.getRetentionYears()));
  }
}
