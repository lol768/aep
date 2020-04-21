# --- !Ups

create table tabula_assignment (
    id uuid not null,
    name varchar,
    academic_year int,
    created_utc timestamp(3) NOT NULL,
    version_utc timestamp(3) NOT NULL,
    version_user varchar,
    constraint pk_tabula_assignment primary key (id)
);

create table tabula_assignment_version (
    id uuid not null,
    name varchar,
    academic_year int,
    created_utc timestamp(3) not null,
    version_utc timestamp(3) not null,
    version_operation char(6) not null,
    version_timestamp_utc timestamp(3) not null,
    version_user varchar,
    constraint pk_tabula_assignment_version primary key (id, version_timestamp_utc)
);

create index idx_tabula_assignment_version on tabula_assignment_version (id, version_utc);

# --- !Downs

drop table tabula_assignment;
drop table tabula_assignment_version;
