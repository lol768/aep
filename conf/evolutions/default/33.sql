# --- !Ups

alter table assessment_client_network_activity add column local_timezone_name varchar;

# --- !Downs

alter table assessment_client_network_activity drop column local_timezone_name;
