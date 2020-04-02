# --- !Ups
update qrtz_cron_triggers
set cron_expression = '0 30 * * * ?'
where trigger_name in (select trigger_name from qrtz_triggers where job_name = 'ImportAssessment');


# --- !Downs
update qrtz_cron_triggers
set cron_expression = '0 0 7 * * ?'
where trigger_name in (select trigger_name from qrtz_triggers where job_name = 'ImportAssessment');
