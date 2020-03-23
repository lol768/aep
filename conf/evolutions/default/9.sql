# --- !Ups

create table announcement (
   id uuid not null,
   assessment_id uuid not null references assessment (id),
   text text not null,
   CREATED_UTC timestamp(3) NOT NULL,
   VERSION_UTC timestamp(3) NOT NULL,
   VERSION_USER varchar,
   CONSTRAINT pk_announcement PRIMARY KEY (ID)
);

create index idx_announcement_assessment on announcement (assessment_id);

create table announcement_version (
    id uuid not null,
    assessment_id uuid not null references assessment (id),
    text text not null,
    CREATED_UTC timestamp(3) NOT NULL,
    VERSION_UTC timestamp(3) NOT NULL,
    VERSION_OPERATION char(6) NOT NULL,
    VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
    VERSION_USER varchar,
    CONSTRAINT pk_announcement_version PRIMARY KEY (ID, VERSION_TIMESTAMP_UTC)
);

CREATE INDEX IDX_ANNOUNCEMENT_VERSION ON ANNOUNCEMENT_VERSION (ID, VERSION_UTC);

# --- !Downs

drop table announcement_version;
drop table announcement;

