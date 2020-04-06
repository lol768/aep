# --- !Ups

update assessment set state = 'Approved' where state = 'Submitted';

# --- !Downs
