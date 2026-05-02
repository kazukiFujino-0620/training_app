INSERT INTO training_item_master (
  parts_code,
  item_name,
  display_order
) VALUES (
  /* entities.partCode */'0101',
  /* entities.itemName */'ベンチプレス',
  /* entities.displayOrder */1
)
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order)