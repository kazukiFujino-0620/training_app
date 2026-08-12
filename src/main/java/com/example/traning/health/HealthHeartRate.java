package com.example.traning.health;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "health_heart_rate")
public class HealthHeartRate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "user_id")
  public Long userId;

  @Column(name = "record_date")
  public LocalDate recordDate;

  @Column(name = "avg_bpm")
  public Integer avgBpm;

  @Column(name = "min_bpm")
  public Integer minBpm;

  @Column(name = "max_bpm")
  public Integer maxBpm;

  @Column(name = "source")
  public String source;

  @Column(name = "synced_at", insertable = false, updatable = false)
  public LocalDateTime syncedAt;
}
