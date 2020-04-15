# --- !Ups

<<<<<<< HEAD
update uploaded_file set owner_type = 'AssessmentBrief' where owner_type = 'Assessment';
update uploaded_file_version set owner_type = 'AssessmentBrief' where owner_type = 'Assessment';

# --- !Downs

update uploaded_file set owner_type = 'Assessment' where owner_type = 'AssessmentBrief';
update uploaded_file_version set owner_type = 'Assessment' where owner_type = 'AssessmentBrief';
=======
alter table  assessment add column tabula_assignments text[] not null default '{}';
alter table  assessment_version add column tabula_assignments text[] not null default '{}';

# --- !Downs

alter table assessment drop column tabula_assignments;
alter table assessment_version drop column tabula_assignments;
>>>>>>> develop
