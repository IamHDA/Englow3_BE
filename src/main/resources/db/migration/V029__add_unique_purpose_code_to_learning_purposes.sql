alter table learning_purposes
    add constraint uq_learning_purposes_purpose_code unique (purpose_code);
