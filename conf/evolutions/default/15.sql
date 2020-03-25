# --- !Ups

alter table assessment ADD COLUMN invigilators text[];
alter table assessment_version ADD COLUMN invigilators text[];

# --- !Downs

alter table assessment DROP COLUMN invigilators;
alter table assessment_version DROP COLUMN invigilators;
