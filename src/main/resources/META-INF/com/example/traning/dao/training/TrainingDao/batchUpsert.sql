INSERT INTO training_item_master (
  part_code,
  item_name,
  display_order
) VALUES (
  /*%entity.partCode*/'part',
  /*%entity.itemName*/'item',
  /*%entity.displayOrder*/1
)
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);