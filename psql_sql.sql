---Manually select rows, after filtering by month number
select * from cdr_holder where extract(month from to_timestamp(unix_start)) = 12

---Manually check total time by month and msisdn
select incoming, sum(smf)
from (
	select *, (unix_end - unix_start) as smf
	from cdr_holder ch
	where extract(month from TO_TIMESTAMP(unix_start)) = 12 and sim = '787845253770'
) sub
group by (incoming);

---SQL request for unit-test testSqlInsertNewArchive (sqlInsertNewArchive in GenerateCallStory)
select sim, unix_start, unix_end, incoming
from cdr_holder
order by id desc