# --- !Ups

alter table assessment add column duration_style text not null default 'DayWindow';
alter table assessment_version add column duration_style text not null default 'DayWindow';

# --- !Downs

alter table assessment drop column duration_style;
alter table assessment_version drop column duration_style;
