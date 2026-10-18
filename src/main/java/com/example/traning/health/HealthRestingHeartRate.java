package com.example.traning.health;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/**
 * 安静時心拍（機能見直し-1-#5）。1ユーザー1日1行で、手動入力とHealthKit/Health Connect同期のうち最後に書き込んだ値を保持する。
 *
 * <p>health_heart_rate（日中の平均・最小・最大）とは別指標のため別テーブルにしている（設計書0節・QA Q5-2）。
 */
@Entity
@Table(name = "health_resting_heart_rate")
public class HealthRestingHeartRate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "user_id")
  public Long userId;

  @Column(name = "record_date")
  public LocalDate recordDate;

  @Column(name = "resting_bpm")
  public Integer restingBpm;

  /** MANUAL / HEALTHKIT / HEALTH_CONNECT */
  @Column(name = "source")
  public String source;

  @Column(name = "created_at", insertable = false, updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  public LocalDateTime updatedAt;
}
