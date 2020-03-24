# --- !Ups

alter table student_assessment_version add column finalise_time_utc timestamp(3);

# --- !Downs

alter table student_assessment_version drop column finalise_time_utc;
