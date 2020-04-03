# --- !Ups
alter table uploaded_file
    add column upload_started_utc timestamp(3);
update uploaded_file
    set upload_started_utc = created_utc;
alter table uploaded_file
    alter column upload_started_utc set not null;

alter table uploaded_file_version
    add column upload_started_utc timestamp(3);
update uploaded_file_version
    set upload_started_utc = created_utc;
alter table uploaded_file_version
    alter column upload_started_utc set not null;

# --- !Downs
alter table uploaded_file
    drop column upload_started_utc;

alter table uploaded_file_version
    drop column upload_started_utc;
