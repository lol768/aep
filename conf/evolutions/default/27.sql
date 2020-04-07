# --- !Ups

alter table  assessment alter column duration drop not null;
alter table  assessment_version alter column duration drop not null;

# --- !Downs

alter table  assessment alter column duration set not null;
alter table  assessment_version alter column duration set not null;
