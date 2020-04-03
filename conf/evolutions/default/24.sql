# --- !Ups

create table assessment_client_network_activity (
  downlink numeric(12,4),
  downlink_max numeric(12,4),
  effective_type varchar,
  rtt int,
  type varchar,
  student_assessment_id uuid not null,
  timestamp_utc timestamp(3) not null,
  constraint fk_student_assessment foreign key (student_assessment_id) references student_assessment(id)
);

create unique index idx_assessment_client_network_activity on assessment_client_network_activity (student_assessment_id);

# --- !Downs

drop index idx_assessment_client_network_activity;
drop table assessment_client_network_activity;
