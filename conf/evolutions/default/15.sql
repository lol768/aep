# --- !Ups

alter table assessment add column state varchar not null default 'Draft';
alter table assessment_version add column state varchar not null default 'Draft';

# --- !Downs

alter table assessment_version drop column state;
alter table assessment drop column state;

