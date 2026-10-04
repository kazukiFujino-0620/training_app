SELECT id, proposal_id, week_number, day_of_week, part_code
FROM program_cycle_proposal_day_templates
WHERE proposal_id = /* proposalId */0
ORDER BY week_number, FIELD(day_of_week, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')
