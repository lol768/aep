# --- !Ups

alter table  assessment add column tabula_assignments text[] not null default '{}';
alter table  assessment_version add column tabula_assignments text[] not null default '{}';

# --- !Downs

alter table assessment drop column tabula_assignments;
alter table assessment_version drop column tabula_assignments;
