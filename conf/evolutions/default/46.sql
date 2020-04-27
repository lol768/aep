# --- !Ups
alter table assessment
    alter column tabula_assignments drop default,
    alter column tabula_assignments set data type uuid[] using tabula_assignments::uuid[],
    alter column tabula_assignments set default '{}';
alter table assessment_version
    alter column tabula_assignments drop default,
    alter column tabula_assignments set data type uuid[] using tabula_assignments::uuid[],
    alter column tabula_assignments set default '{}';

# --- !Downs
alter table assessment
    alter column tabula_assignments drop default,
    alter column tabula_assignments set data type text[],
    alter column tabula_assignments set default '{}';
alter table assessment_version
    alter column tabula_assignments drop default,
    alter column tabula_assignments set data type text[] using tabula_assignments::uuid[],
    alter column tabula_assignments set default '{}';
