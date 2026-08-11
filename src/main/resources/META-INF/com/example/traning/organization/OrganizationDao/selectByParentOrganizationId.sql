select * from organizations
where parent_organization_id = /* parentOrganizationId */1
and deleted_at IS NULL
order by id
