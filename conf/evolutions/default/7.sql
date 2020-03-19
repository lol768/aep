# --- !Ups

CREATE TABLE STUDENT_ASSESSMENT (
  ASSESSMENT_ID uuid NOT NULL,
  STUDENT_ID varchar NOT NULL,
  IN_SEAT BOOLEAN NOT NULL DEFAULT FALSE,
  START_TIME_UTC timestamp(3),
  UPLOADED_FILE_IDS UUID[],
  CREATED_UTC timestamp(3) NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_USER varchar,
  CONSTRAINT PK_STUDENT_ASSESSMENT PRIMARY KEY (ASSESSMENT_ID, STUDENT_ID),
  CONSTRAINT FK_STUDENT_ASSESSMENT FOREIGN KEY (ASSESSMENT_ID) REFERENCES ASSESSMENT(ID)
);


CREATE TABLE STUDENT_ASSESSMENT_VERSION (
    ASSESSMENT_ID uuid NOT NULL,
    STUDENT_ID varchar(7) NOT NULL,
    IN_SEAT BOOLEAN NOT NULL DEFAULT FALSE,
    START_TIME_UTC timestamp(3),
    UPLOADED_FILE_IDS UUID[],CREATED_UTC timestamp(3) NOT NULL,
    VERSION_UTC timestamp(3) NOT NULL,
    VERSION_OPERATION char(6) NOT NULL,
    VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
    VERSION_USER varchar,
    CONSTRAINT PK_STUDENT_ASSESSMENT_VERSION PRIMARY KEY (ASSESSMENT_ID, STUDENT_ID, VERSION_TIMESTAMP_UTC)
);

CREATE INDEX IDX_STUDENT_ASSESSMENT_VERSION ON STUDENT_ASSESSMENT_VERSION (ASSESSMENT_ID, STUDENT_ID, VERSION_UTC);

ALTER TABLE ASSESSMENT RENAME COLUMN START_TIME TO START_TIME_UTC;
ALTER TABLE ASSESSMENT_VERSION RENAME COLUMN START_TIME TO START_TIME_UTC;

# --- !Downs
DROP TABLE STUDENT_ASSESSMENT_VERSION;
DROP TABLE STUDENT_ASSESSMENT;


ALTER TABLE ASSESSMENT RENAME COLUMN START_TIME_UTC TO START_TIME;
ALTER TABLE ASSESSMENT_VERSION RENAME COLUMN START_TIME_UTC TO START_TIME;
