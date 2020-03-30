# --- !Ups

alter table assessment ADD COLUMN invigilators text[] not null default '{}';
alter table assessment_version ADD COLUMN invigilators text[] not null default '{}';

# --- !Downs

alter table assessment DROP COLUMN invigilators;
alter table assessment_version DROP COLUMN invigilators;