# --- !Ups

alter table message add column staff_id text;
alter table message_version add column staff_id text;

update message set sender = 'Student' where sender = 'Client';
update message_version set sender = 'Student' where sender = 'Client';


# --- !Downs

alter table message drop column staff_id;
alter table message_version drop column staff_id;

update message set sender = 'Client' where sender = 'Student';
update message_version set sender = 'Client' where sender = 'Student';

