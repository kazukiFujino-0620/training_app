SELECT COUNT(*)
FROM audit_logs
WHERE changed_at >= /* from */'2026-01-01'
  AND changed_at <  ADDDATE(/* to */'2026-12-31', INTERVAL 1 DAY)
  /*%if userId != null */
  AND user_id = /* userId */0
  /*%end*/
  /*%if action != null && !action.isEmpty() */
  AND action = /* action */'TRAINING_SAVE'
  /*%end*/
