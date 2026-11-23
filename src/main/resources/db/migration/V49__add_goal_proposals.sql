-- 機能見直し-1-#20 目標をトレーナーと一緒に決める

-- トレーナー（担当トレーナー・担当範囲の管理者）からの目標の提案。会員が承認すると週の回数目標と目的モードが確定する。
-- 体重・体型の目標は持たない（会議の決定）。返事待ちの提案は会員ごとに最新の1件だけで、新しい提案を送ると前の提案は取り下げ（WITHDRAWN）。
-- 見直し日の印（担当会員の一覧）は、会員ごとに最新の提案の next_review_date で判定する。
CREATE TABLE goal_proposals (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_user_id      BIGINT       NOT NULL COMMENT '宛先の会員（users.id）',
  proposer_user_id    BIGINT       NOT NULL COMMENT '提案したスタッフ（users.id）',
  weekly_target_count INT          NOT NULL COMMENT '週の回数目標（1〜7）',
  goal_mode           VARCHAR(20)  NOT NULL COMMENT 'トレーニング目的（BULKING/CUTTING/MAINTENANCE）',
  comment             VARCHAR(50)  NULL     COMMENT 'ひとこと（任意、50文字まで）',
  next_review_date    DATE         NOT NULL COMMENT '次の見直し日',
  status              VARCHAR(20)  NOT NULL COMMENT 'PENDING(返事待ち)/ACCEPTED(承認)/DECLINED(見送り)/WITHDRAWN(取り下げ)',
  proposed_at         DATETIME     NOT NULL COMMENT '提案した日時',
  responded_at        DATETIME     NULL     COMMENT '会員が承認・見送りした日時、または取り下げになった日時',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_goal_proposals_member (member_user_id, proposed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会員が自分で週の回数目標・目的モードを変えた記録（Q20-1 A）。スタッフ向けの会員詳細の履歴に「会員が変更」として出す。
-- 承認した提案より後にこの記録があれば、プロフィールの「担当トレーナーと決めた目標」の表示を外す。
CREATE TABLE member_goal_changes (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_user_id BIGINT      NOT NULL COMMENT '会員（users.id）',
  change_type    VARCHAR(20) NOT NULL COMMENT 'WEEKLY_TARGET(週の回数目標)/GOAL_MODE(目的モード)',
  old_value      VARCHAR(20) NULL     COMMENT '変更前の値（週の回数目標は回数、目的モードはBULKING等。未設定はNULL）',
  new_value      VARCHAR(20) NOT NULL COMMENT '変更後の値',
  changed_at     DATETIME    NOT NULL COMMENT '変更した日時',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_member_goal_changes_member (member_user_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
