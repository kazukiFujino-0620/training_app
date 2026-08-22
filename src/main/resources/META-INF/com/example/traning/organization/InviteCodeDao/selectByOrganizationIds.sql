select * from invite_codes
where organization_id IN /* organizationIds */(1, 2)
order by created_at desc
