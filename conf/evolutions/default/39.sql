# --- !Ups

alter table student_assessment
    add column occurrence varchar,
    add column academic_year integer;

alter table student_assessment_version
    add column occurrence varchar,
    add column academic_year integer;

# --- !Downs

alter table student_assessment
    drop column occurrence,
    drop column academic_year;

alter table student_assessment_version
    drop column occurrence,
    drop column academic_year;
