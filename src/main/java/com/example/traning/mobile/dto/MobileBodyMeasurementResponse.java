package com.example.traning.mobile.dto;

import com.example.traning.body.BodyMeasurement;
import java.time.LocalDate;

/**
 * モバイル: 体重・体脂肪率記録のレスポンス（ita7-1）。
 *
 * <p>Web用API/モバイル用APIは別DTOという既存方針に沿い、{@link BodyMeasurement} エンティティを直接返さずこのDTO経由で返す。
 */
public record MobileBodyMeasurementResponse(
    Long id,
    LocalDate measuredDate,
    Double weightKg,
    Double bodyFatPct,
    String memo,
    String source) {

  public static MobileBodyMeasurementResponse from(BodyMeasurement m) {
    return new MobileBodyMeasurementResponse(
        m.id, m.measuredDate, m.weightKg, m.bodyFatPct, m.memo, m.source);
  }
}
