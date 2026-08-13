-- training_item_master の part_code / item_name の照合順序を accent-sensitive に変更（ita1-4 バッチ障害の恒久対応）
-- 従来の utf8mb4_0900_ai_ci は accent-insensitive のため、濁点・半濁点の違い（例: 「ショルダープレスマシン」と
-- 「ショルダーブレスマシン」）を同一文字列とみなしてしまい、CSV取り込みバッチの UNIQUE KEY (uq_org_part_item)
-- 判定で誤って重複エラーとなり、バッチ全体がロールバックされる不具合があった。
-- utf8mb4_0900_as_cs（accent-sensitive, case-sensitive）に変更し、濁点・半濁点を正しく区別させる。
ALTER TABLE training_item_master
  MODIFY part_code VARCHAR(50) NOT NULL COLLATE utf8mb4_0900_as_cs,
  MODIFY item_name VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs;
