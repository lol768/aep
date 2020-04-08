# --- !Ups

alter table  assessment alter column type drop not null;
alter table  assessment_version alter column type drop not null;

# --- !Downs

-- not making any assumptions about what to do if this is reversed (set defaults for rows with null or delete them)
alter table assessment alter column type set not null;
alter table assessment_version alter column type set not null;
