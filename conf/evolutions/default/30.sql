# --- !Ups

update assessment
    set duration = null, type = null, platform = ''
    where tabula_assessment_id is not null and brief = '{"fileIds": []}' and exam_profile_code in ('EXMAY20', 'EXJUN20_V2');

# --- !Downs
-- nope
