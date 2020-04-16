# --- !Ups

alter table assessment_client_network_activity alter column student_assessment_id drop not null;
alter table assessment_client_network_activity add column assessment_id uuid;
alter table assessment_client_network_activity add column usercode varchar;
create index idx_assessment_client_network_activity_assessment on assessment_client_network_activity (assessment_id);

# --- !Downs

drop index idx_assessment_client_network_activity_assessment;
alter table assessment_client_network_activity drop column usercode;
alter table assessment_client_network_activity drop column assessment_id;
delete from assessment_client_network_activity where student_assessment_id is null;
alter table assessment_client_network_activity alter column student_assessment_id set not null;
