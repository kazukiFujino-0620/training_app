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
@Table(name = "health_sleep")
public class HealthSleep {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "user_id")
  public Long userId;

  @Column(name = "sleep_date")
  public LocalDate sleepDate;

  @Column(name = "start_time")
  public LocalDateTime startTime;

  @Column(name = "end_time")
  public LocalDateTime endTime;

  @Column(name = "duration_minutes")
  public Integer durationMinutes;

  @Column(name = "source")
  public String source;

  @Column(name = "synced_at", insertable = false, updatable = false)
  public LocalDateTime syncedAt;
}
