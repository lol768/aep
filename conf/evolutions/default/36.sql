# --- !Ups

create index idx_assessment_client_network_activity_timestamp on assessment_client_network_activity (timestamp_utc);

# --- !Downs

drop index idx_assessment_client_network_activity_timestamp;
