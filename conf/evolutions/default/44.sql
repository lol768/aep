# --- !Ups

update qrtz_cron_triggers set cron_expression =  '0 0 * * * ?'
where trigger_name = (select trigger_name from qrtz_triggers where job_name = 'TriggerSubmissionUploads');



# --- !Downs
-- nope
