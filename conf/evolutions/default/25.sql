# --- !Ups

drop index idx_assessment_client_network_activity;
create index idx_assessment_client_network_activity on assessment_client_network_activity (student_assessment_id);

# --- !Downs

drop index idx_assessment_client_network_activity;
create unique index idx_assessment_client_network_activity on assessment_client_network_activity (student_assessment_id);
