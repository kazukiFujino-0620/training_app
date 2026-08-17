select * from organizations
where id IN /* ids */(1, 2)
and deleted_at IS NULL
order by type, name
