-- 機能見直し-1-#15 トレーナー権限と担当範囲での閲覧制限（itバグ-44 を同時に修正）

-- 招待コードの用途（itバグ-44・Q15-4）。MEMBER=会員の新規登録（/signup）用、TRAINER=トレーナー登録（/signup/trainer）用。
-- 既存のコードはすべて会員用として扱う（DEFAULTで埋まる）。
ALTER TABLE invite_codes
  ADD COLUMN purpose VARCHAR(10) NOT NULL DEFAULT 'MEMBER' COMMENT 'MEMBER(会員用) / TRAINER(トレーナー用)' AFTER code;

-- 役割の確認が必要なスタッフ（既存の移行）。トレーナー登録で作られた店舗管理者と、ジム管理者が任命した店舗管理者は
-- データから区別できない（/signup/trainer は監査ログを残しておらず、権限変更の監査ログも対象ユーザーIDを持たない）ため、
-- 今いる店舗管理者全員を対象にする。ジム管理者が役割を選び直すと0に戻る。選び直すまでは今の権限のまま。
ALTER TABLE users
  ADD COLUMN role_review_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=役割の確認が必要（#15の移行対象）';

UPDATE users
SET role_review_required = 1
WHERE role = 'ROLE_STORE_ADMIN'
  AND deleted_at IS NULL;

-- ジムごとの設定（Q15-2）。行が無いジムは全項目が初期値（控えめな側）として扱う。
-- 今後のジム単位の設定（やり取り・動画・問診・けがの記録の使用可否など）もこの表に列を足して使う。
CREATE TABLE gym_settings (
  organization_id          BIGINT     NOT NULL PRIMARY KEY COMMENT 'organizations.id（type=GYM）',
  staff_health_info_visible TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=店舗管理者・ジム管理者が会員の健康に関わる情報を見られる（閲覧は監査ログに記録）',
  updated_by               BIGINT     NULL COMMENT '最後に変更したusers.id',
  updated_at               DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
