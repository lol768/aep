# --- !Ups
alter table assessment
  add column tabula_assessment_id uuid ,
  add column module_code varchar(255),
  add column department_code varchar(100);

alter table assessment_version
  add column tabula_assessment_id uuid ,
  add column module_code varchar(255),
  add column department_code varchar(100);


update assessment set module_code = 'XX101-10', department_code = 'XX';
update assessment_version set module_code = 'XX101-10', department_code = 'XX';

alter table assessment alter column module_code set not null, alter column department_code set not null;
alter table assessment_version alter column module_code set not null, alter column department_code set not null;


# --- !Downs
alter table assessment drop column tabula_assessment_id, drop column module_code, drop column department_code;
alter table assessment_version drop column tabula_assessment_id, drop column module_code, drop column department_code;
