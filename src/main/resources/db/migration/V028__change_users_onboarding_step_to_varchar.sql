alter table users alter column onboarding_step drop default;

-- every existing row is still at the first step: no application code has ever written this column
alter table users
    alter column onboarding_step type varchar(30)
    using 'LEARNING_PURPOSES';

alter table users alter column onboarding_step set default 'LEARNING_PURPOSES';
