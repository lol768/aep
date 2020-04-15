# --- !Ups

update uploaded_file set owner_type = 'AssessmentBrief' where owner_type = 'Assessment';
update uploaded_file_version set owner_type = 'AssessmentBrief' where owner_type = 'Assessment';

# --- !Downs

update uploaded_file set owner_type = 'Assessment' where owner_type = 'AssessmentBrief';
update uploaded_file_version set owner_type = 'Assessment' where owner_type = 'AssessmentBrief';
