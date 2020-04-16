# --- !Ups

alter table announcement add column sender varchar;
alter table announcement_version add column sender varchar;
update announcement set sender = '';
update announcement_version set sender = '';

# --- !Downs

alter table announcement drop column sender;
alter table announcement_version drop column sender;
