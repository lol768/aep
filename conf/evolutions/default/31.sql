# --- !Ups

update assessment
    set duration = null
    where tabula_assessment_id is not null and brief = '{"fileIds": []}' and exam_profile_code = 'EXAPR20V2';

# --- !Downs
-- nope
