package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * モバイル: トレーニング完了API（POST /api/mobile/training/complete）のリクエスト。
 *
 * - trainingId: 完了対象のトレーニングID（必須）
 * - durationSec: セッションの経過秒数（任意）。指定された場合、当日（completeしたトレーニングと同じ日付）の
 *                同一ユーザーの全トレーニングの duration カラムに「秒数の文字列」として保存する。
 *                これによりモバイル側は次回再開時に MAX(duration) を読み取って復元できる。
 *                null/未指定なら duration の更新はスキップ（冪等）。
 */
@Data
public class CompleteTrainingRequest {

	private Long trainingId;

	@Min(0)
	private Integer durationSec;
}
