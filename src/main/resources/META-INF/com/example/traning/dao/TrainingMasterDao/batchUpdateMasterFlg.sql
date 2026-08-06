UPDATE training_item_master
SET
  master_flg = /*%entity.masterFlg*/1
WHERE
  id = /*%entity.id*/1
