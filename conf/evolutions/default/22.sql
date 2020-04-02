# --- !Ups

create table assessment_client_network_activity (
  downlink numeric(12,4),
  downlink_max numeric(12,4),
  effective_type varchar,
  rtt int,
  type varchar,
  student_assessment_id uuid not null,
  timestamp_utc timestamp(3) not null
);


# --- !Downs

drop table assessment_client_network_activity;
