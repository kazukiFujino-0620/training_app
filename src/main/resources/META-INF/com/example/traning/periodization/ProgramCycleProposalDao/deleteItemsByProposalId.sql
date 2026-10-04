DELETE i FROM program_cycle_proposal_day_template_items i
JOIN program_cycle_proposal_day_templates d ON d.id = i.day_template_id
WHERE d.proposal_id = /* proposalId */0
