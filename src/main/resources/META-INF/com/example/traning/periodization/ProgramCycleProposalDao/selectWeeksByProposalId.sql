SELECT id, proposal_id, week_number, target_intensity_pct, is_deload
FROM program_cycle_proposal_weeks
WHERE proposal_id = /* proposalId */0
ORDER BY week_number
