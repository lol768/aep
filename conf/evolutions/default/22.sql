# --- !Ups
alter table assessment add column section varchar;
alter table assessment_version add column section varchar;

drop index idx_assessment_papercode;
create index idx_assessment_papercode on assessment (paper_code, section, exam_profile_code);

# --- !Downs
alter table assessment drop column section;
alter table assessment_version drop column section;

drop index idx_assessment_papercode;
create index idx_assessment_papercode on assessment (paper_code, exam_profile_code);
