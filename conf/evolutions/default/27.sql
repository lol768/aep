# --- !Ups

create table declarations (
    id uuid not null,
    accepts_authorship boolean not null,
    self_declared_ra boolean not null,
    completed_ra boolean not null,
    created_utc timestamp(3) not null,
    version_utc timestamp(3) not null,
    constraint pk_declarations primary key (id)
);

create table declarations_version (
    id uuid not null,
    accepts_authorship boolean not null,
    self_declared_ra boolean not null,
    completed_ra boolean not null,
    created_utc timestamp(3) not null,
    version_utc timestamp(3) not null,
    version_operation char(6) not null,
    version_timestamp_utc timestamp(3) not null,
    version_user varchar,
    constraint pk_declarations_version primary key (id, version_timestamp_utc)
);

create index idx_declarations_version on declarations_version (id, version_utc);

# --- !Downs

drop table declarations;
drop table declarations_version;

