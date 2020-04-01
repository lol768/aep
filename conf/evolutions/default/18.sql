# --- !Ups
create table message (
  id uuid not null,
  sender text not null,
  text text not null,
  university_id text not null,
  assessment_id uuid not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  constraint pk_message primary key (id)
);

create table message_version (
  id uuid not null,
  sender text not null,
  text text not null,
  university_id text not null,
  assessment_id uuid not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user text null,
  constraint pk_message_version primary key (id, version_timestamp_utc)
);

create index idx_message on message(assessment_id, university_id);

# --- !Downs
drop table message_version, message;
