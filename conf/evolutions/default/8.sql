# --- !Ups

alter table student_assessment alter uploaded_file_ids set not null;
alter table student_assessment_version alter uploaded_file_ids set not null;

alter table assessment add column title varchar not null default 'Examination';
alter table assessment_version add column title varchar not null default 'Examination';

alter table assessment rename column assessment_type to platform;
alter table assessment_version rename column assessment_type to platform;

alter type assessment_type rename to assessment_platform;

create type assessment_type as enum (
    'OpenBook',
    'MultipleChoice',
    'Spoken'
);

alter table assessment add column type assessment_type not null default 'OpenBook';
alter table assessment_version add column type assessment_type not null default 'OpenBook';

# --- !Downs

alter table assessment_version drop column type;
alter table assessment drop column type;
drop type assessment_type;

alter type assessment_platform rename to assessment_type;
alter table assessment_version rename column platform to assessment_type;
alter table assessment rename column platform to assessment_type;

alter table assessment_version drop column title;
alter table assessment drop column title;

alter table student_assessment_version alter uploaded_file_ids drop not null;
alter table student_assessment alter uploaded_file_ids drop not null;

