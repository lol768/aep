# --- !Ups

update assessment
    set duration = null, type = null, platform = null
    where tabula_assessment_id is not null and brief = '{"fileIds": []}'; -- Assume brief will have changed if edited by department

# --- !Downs
-- nope