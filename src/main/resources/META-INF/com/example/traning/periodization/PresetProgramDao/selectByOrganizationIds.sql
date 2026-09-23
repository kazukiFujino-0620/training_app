SELECT id, organization_id, name, purpose_category, total_weeks, description, display_order, created_at
FROM preset_programs
WHERE organization_id IN /* organizationIds */(0)
ORDER BY purpose_category, display_order, id
