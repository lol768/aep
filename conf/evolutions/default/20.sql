# --- !Ups
alter table assessment
  add column exam_profile_code varchar,
  alter column module_code type varchar,
  alter column department_code type varchar,
  alter column sequence type varchar;

alter table assessment rename column code to paper_code;

drop index idx_tabula_assessment;
create unique index idx_assessment_tabula on assessment (tabula_assessment_id, exam_profile_code);

drop index idx_assessment_code;
create index idx_assessment_papercode on assessment (paper_code, exam_profile_code);

alter table assessment_version
    add column exam_profile_code varchar,
    alter column module_code type varchar,
    alter column department_code type varchar,
    alter column sequence type varchar;

alter table assessment_version rename column code to paper_code;

update assessment set exam_profile_code = 'EXAPR20';
update assessment_version set exam_profile_code = 'EXAPR20';

alter table assessment alter column exam_profile_code set not null;
alter table assessment_version alter column exam_profile_code set not null;


# --- !Downs
drop index idx_assessment_tabula;
create unique index idx_tabula_assessment on assessment (tabula_assessment_id);

drop index idx_assessment_papercode;
create index idx_assessment_code on assessment (paper_code);

alter table assessment rename column paper_code to code;
alter table assessment_version rename column paper_code to code;

-- Don't bother making the column types restrictive again
alter table assessment drop column exam_profile_code;
alter table assessment_version drop column exam_profile_code;
