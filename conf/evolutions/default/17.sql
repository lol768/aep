# --- !Ups

alter table student_assessment ADD COLUMN extra_time_adjustment interval;
alter table student_assessment_version ADD COLUMN extra_time_adjustment interval;

# --- !Downs

alter table student_assessment DROP COLUMN extra_time_adjustment;
alter table student_assessment_version DROP COLUMN extra_time_adjustment;