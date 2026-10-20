-- 機能見直し-1-#7: 継続日数表示を「週の回数目標・達成率」ベースに置き換え
-- 詳細設計書「機能見直し-1-#7 - 継続日数表示を頻度目標・達成率ベースに置き換え_設計書」1章・5章（QA Q7-1〜Q7-8）。

-- ===== ユーザーが手入力した週の回数目標（1ユーザー1行。設計書1章） =====
-- 期分けプログラム（program_cycles）の対象外の週にだけ使う（QA Q7-7）。
CREATE TABLE user_frequency_goals (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT NOT NULL,
  weekly_target_count INT NOT NULL COMMENT '週の回数目標（1〜7。例: 3 = 週3日トレーニングする）',
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_frequency_goals_user (user_id)
);

-- ===== 週ごとの確定目標（スナップショット。設計書5-2節） =====
-- 1ユーザー×1週（月曜起点）につき1行。一度書いた行は書き換えない（目標変更・プログラム開始/終了で
-- 過去の週の達成/未達がさかのぼって変わらないようにするため）。例外は評価に含めない「目標を設定した週」の行のみ
-- （同じ週のうちに目標を選び直した場合、その週の表示を新しい値にそろえる）。
CREATE TABLE user_frequency_week_targets (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  week_start_date  DATE NOT NULL COMMENT 'その週の月曜日',
  target_count     INT NOT NULL COMMENT 'その週の目標日数。0=目標なし（評価しない）',
  source           VARCHAR(10) NOT NULL COMMENT 'MANUAL(手入力の目標)/PROGRAM(期分けプログラムの予定日数)/NONE(目標なし)',
  program_cycle_id BIGINT NULL COMMENT 'source=PROGRAMのとき目標の元にしたprogram_cycles.id',
  evaluated        TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=達成率の計算に含めない（目標を設定した週＝途中の週。QA Q7-8）',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_frequency_week (user_id, week_start_date)
);
