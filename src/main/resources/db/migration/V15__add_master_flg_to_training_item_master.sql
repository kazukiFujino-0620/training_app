ALTER TABLE training_item_master
  ADD COLUMN master_flg INT NOT NULL DEFAULT 1 COMMENT '種目活用フラグ（0:使用不可, 1:使用可能）';
