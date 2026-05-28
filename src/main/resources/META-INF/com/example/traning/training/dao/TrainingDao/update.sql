update trainings
set
  menu = /* training.menu */'dummy',
  part_code = /* training.partCode */'CHEST',
  is_all_completed = /* training.allCompleted */'f',
  memo = /* training.memo */'memo',
  duration = /* training.duration */'00:00:00',
  updated_datetime = /* training.updatedDatetime */'2026-01-01'
where
  id = /* training.id */1