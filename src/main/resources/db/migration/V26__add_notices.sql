-- ita2-5: トレーニングジム案内メッセージ対応
-- 配信単位はジム・店舗単位のみ（全体配信なし）。既読管理は行わず、
-- ユーザーが閲覧（一覧画面表示）した時点でそのユーザーには表示されなくなる
-- （notice_dismissalsに記録、notices本体は他ユーザーのために残る）。

CREATE TABLE notices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL COMMENT 'organizations.id を参照。配信対象の組織/店舗',
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created_by BIGINT NOT NULL COMMENT 'users.id を参照。作成した管理者',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  KEY idx_notices_organization (organization_id)
);

CREATE TABLE notice_dismissals (
  notice_id BIGINT NOT NULL COMMENT 'notices.id を参照',
  user_id BIGINT NOT NULL COMMENT 'users.id を参照',
  dismissed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notice_id, user_id)
);
