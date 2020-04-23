# --- !Ups

alter table assessment add column duration_style text not null default 'DayWindow';
alter table assessment_version add column duration_style text not null default 'DayWindow';

# --- !Downs

alter table assessment drop column duration_style;
alter table assessment_version drop column duration_style;
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
