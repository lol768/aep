# --- !Ups

alter table student_assessment add column finalise_time_utc timestamp(3);

# --- !Downs

alter table student_assessment drop column finalise_time_utc;
