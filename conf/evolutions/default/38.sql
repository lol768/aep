# --- !Ups

alter table announcement add column sender varchar;
alter table announcement_version add column sender varchar;

# --- !Downs

alter table announcement drop column sender;
alter table announcement_version drop column sender;
