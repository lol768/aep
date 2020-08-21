# --- !Ups
update qrtz_cron_triggers set cron_expression = '0-3,6-23 5 * * * ?' where trigger_name = (
    select trigger_name from qrtz_triggers where job_name = 'TriggerSubmissionUploads'
);

update qrtz_cron_triggers set cron_expression = '0-3,6-23 30 * * * ?' where trigger_name = (
    select trigger_name from qrtz_triggers where job_name = 'ImportAssessment'
);

# --- !Downs
update qrtz_cron_triggers set cron_expression = '0 0 * * * ?' where trigger_name = (
    select trigger_name from qrtz_triggers where job_name = 'TriggerSubmissionUploads'
);

update qrtz_cron_triggers set cron_expression = '0 30 * * * ?' where trigger_name = (
    select trigger_name from qrtz_triggers where job_name = 'ImportAssessment'
);
