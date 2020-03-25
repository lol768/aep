# --- !Ups

alter table assessment
  alter column type drop default,
  alter column type type varchar,
  alter column type set default 'OpenBook',
  alter column platform type varchar;

alter table assessment_version
  alter column type drop default,
  alter column type type varchar,
  alter column type set default 'OpenBook',
  alter column platform type varchar;

drop type assessment_type;
drop type assessment_platform;

# --- !Downs

create type assessment_type as enum (
    'OpenBook',
    'MultipleChoice',
    'Spoken'
);

create type assessment_platform as enum (
    'Moodle',
    'OnlineExams',
    'QuestionmarkPerception'
);

alter table assessment
  alter column type drop default,
  alter column type type assessment_type
  alter column type set default 'OpenBook',
  alter column platform type assessment_platform;

alter table assessment_version
  alter column type drop default,
  alter column type type assessment_type
  alter column type set default 'OpenBook',
  alter column platform type assessment_platform;
