# --- !Ups

alter table assessment ADD COLUMN invigilators text[];

# --- !Downs

alter table assessment DROP COLUMN invigilators;

