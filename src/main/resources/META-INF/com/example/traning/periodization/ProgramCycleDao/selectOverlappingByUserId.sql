SELECT id, user_id, name, total_weeks, start_date, tier, source_preset_id, created_by_trainer_id, status, renew_decided_at, created_at, updated_at
FROM program_cycles
WHERE user_id = /* userId */0
  AND start_date <= /* endDate */'2026-12-31'
  AND DATE_ADD(start_date, INTERVAL total_weeks * 7 DAY) > /* startDate */'2026-01-01'
ORDER BY start_date, id
