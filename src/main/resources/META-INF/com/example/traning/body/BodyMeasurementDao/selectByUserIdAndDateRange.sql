SELECT id, user_id, measured_date, weight_kg, body_fat_pct, memo, created_at, updated_at
FROM body_measurements
WHERE user_id = /* userId */0
  AND measured_date BETWEEN /* from */'2020-01-01' AND /* to */'2020-12-31'
ORDER BY measured_date ASC
