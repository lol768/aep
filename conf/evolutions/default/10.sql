# --- !Ups

alter table announcement_version drop constraint announcement_version_assessment_id_fkey;

# --- !Downs

alter table announcement_version add constraint announcement_version_assessment_id_fkey foreign key (assessment_id) references assessment (id);

