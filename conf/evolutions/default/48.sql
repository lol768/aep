# --- !Ups

alter table assessment alter column duration_style drop not null;
alter table assessment alter column duration_style drop default;
alter table assessment_version alter column duration_style drop not null;
alter table assessment_version alter column duration_style drop default;

# --- !Downs

alter table assessment alter column duration_style set default 'DayWindow';
alter table assessment alter column duration_style set not null;
alter table assessment_version alter column duration_style set default 'DayWindow';
alter table assessment_version alter column duration_style set not null;