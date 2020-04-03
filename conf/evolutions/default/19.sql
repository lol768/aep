# --- !Ups
alter table assessment
  add column tabula_assessment_id uuid ,
  add column module_code varchar(255),
  add column department_code varchar(100),
  add column sequence varchar(100);

create unique index idx_tabula_assessment on assessment (tabula_assessment_id);

alter table assessment_version
  add column tabula_assessment_id uuid ,
  add column module_code varchar(255),
  add column department_code varchar(100),
  add column sequence varchar(100);

update assessment set module_code = 'XX101-10', department_code = 'XX', sequence = 'E01';
update assessment_version set module_code = 'XX101-10', department_code = 'XX', sequence = 'E01';


alter table assessment alter column module_code set not null, alter column department_code set not null, alter column sequence set not null;
alter table assessment_version alter column module_code set not null, alter column department_code set not null, alter column sequence set not null;


# --- !Downs
alter table assessment drop column tabula_assessment_id, drop column module_code, drop column department_code, drop column sequence;
alter table assessment_version drop column tabula_assessment_id, drop column module_code, drop column department_code, drop column sequence;
