# --- !Ups

alter table  assessment alter column duration drop not null;
alter table  assessment_version alter column duration drop not null;

# --- !Downs

update assessment_version set duration = interval '2:00:00' where duration is null;
alter table assessment alter column duration set not null;
update assessment_version set duration = interval '2:00:00' where duration is null;
alter table assessment_version alter column duration set not null;
