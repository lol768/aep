# --- !Ups

-- OE-438 force submissions.zip to be regenerated
delete from uploaded_file where owner_type = 'AssessmentSubmissions';


# --- !Downs
-- nope
