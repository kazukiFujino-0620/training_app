ALTER TABLE trainings
  ADD COLUMN superset_group_id BIGINT NULL
      COMMENT 'スーパーセットグループID（同値=同一グループ）'
      AFTER part_code,
  ADD INDEX idx_trainings_superset (superset_group_id);
