# --- !Ups
create type ASSESSMENT_TYPE as enum (
    'Moodle',
    'OnlineExams',
    'QuestionmarkPerception'
);

CREATE TABLE ASSESSMENT (
  ID uuid NOT NULL,
  CODE varchar NOT NULL,
  START_TIME timestamp(3) NOT NULL,
  DURATION INTERVAL NOT NULL,
  ASSESSMENT_TYPE ASSESSMENT_TYPE NOT NULL,
  BRIEF jsonb NOT NULL,
  CREATED_UTC timestamp(3) NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_USER varchar,
  CONSTRAINT PK_ASSESSMENT PRIMARY KEY (ID)
);

CREATE INDEX IDX_ASSESSMENT_CODE ON ASSESSMENT (CODE);

CREATE TABLE ASSESSMENT_VERSION (
  ID uuid NOT NULL,
  CODE varchar NOT NULL,
  START_TIME timestamp(3) NOT NULL,
  DURATION INTERVAL NOT NULL,
  ASSESSMENT_TYPE ASSESSMENT_TYPE NOT NULL,
  BRIEF jsonb NOT NULL,
  CREATED_UTC timestamp(3) NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
  VERSION_USER varchar,
  CONSTRAINT PK_ASSESSMENT_VERSION PRIMARY KEY (ID, VERSION_TIMESTAMP_UTC)
);

CREATE INDEX IDX_ASSESSMENT_VERSION ON ASSESSMENT_VERSION (ID, VERSION_UTC);

# --- !Downs
DROP TABLE ASSESSMENT_VERSION;
DROP TABLE ASSESSMENT;
DROP TYPE ASSESSMENT_TYPE;
