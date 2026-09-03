-- ita3-3: organization_id=2（招待コードなし登録の一般ユーザー受け皿）の名称を、
-- 運用上「デフォルト店舗」が紛らわしいため「一般（未所属）」に変更する。
-- IDはそのまま、名称変更のみ（データ移行・スキーマ変更なし）。
UPDATE organizations SET name = '一般（未所属）' WHERE id = 2;
