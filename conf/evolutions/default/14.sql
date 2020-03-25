# --- !Ups

truncate table student_assessment; -- sorry
alter table student_assessment
    add column id uuid not null,
    drop constraint pk_student_assessment,
    add constraint pk_student_assessment primary key (id);

create unique index ck_student_assessment on student_assessment (assessment_id, student_id);

truncate table student_assessment_version; -- sorry
alter table student_assessment_version add column id uuid not null;

drop index idx_student_assessment_version;
create index idx_student_assessment_version on student_assessment_version (id, version_utc);

# --- !Downs

drop index ck_student_assessment;
alter table student_assessment
    drop column id,
    drop constraint pk_student_assessment,
    add constraint pk_student_assessment primary key (assessment_id, student_id);

alter table student_assessment_version drop column id;
drop index idx_student_assessment_version;
create index idx_student_assessment_version on student_assessment_version (assessment_id, student_id, version_utc);
