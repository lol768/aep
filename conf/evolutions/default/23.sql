# --- !Ups
alter table uploaded_file add column upload_started timestamp(3) not null;
alter table uploaded_file_version add column upload_started timestamp(3) not null;


# --- !Downs
alter table uploaded_file drop column upload_started;
alter table uploaded_file_version drop column upload_started;
