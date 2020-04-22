# --- !Ups

alter table student_assessment
    add column tabula_submission_id uuid;

alter table student_assessment_version
    add column tabula_submission_id uuid;

# --- !Downs

alter table student_assessment
    drop column tabula_submission_id;

alter table student_assessment_version
    drop column tabula_submission_id;