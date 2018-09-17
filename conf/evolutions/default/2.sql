# --- !Ups
CREATE TABLE AUDIT_EVENT (
	ID uuid NOT NULL,
  EVENT_DATE_UTC timestamp NOT NULL,
  OPERATION varchar(255) NOT NULL,
  USERCODE varchar(255),
  DATA text,
  TARGET_ID varchar(255) NOT NULL,
  TARGET_TYPE varchar(255) NOT NULL,
  CONSTRAINT AUDIT_EVENT_PK PRIMARY KEY (ID)
);

CREATE INDEX IDX_AUDIT_EVENT_FK ON AUDIT_EVENT (TARGET_ID, TARGET_TYPE);

# --- !Downs
DROP TABLE AUDIT_EVENT;