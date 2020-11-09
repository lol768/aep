# --- !Ups

alter table declarations alter column self_declared_ra drop not null;
alter table declarations_version alter column self_declared_ra drop not null;

# --- !Downs

alter table declarations_version alter column self_declared_ra set not null;
alter table declarations alter column self_declared_ra set not null;
