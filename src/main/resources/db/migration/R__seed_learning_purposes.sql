insert into learning_purposes (purpose_code, display_name)
values
    ('CERTIFICATE', 'Luyện thi chứng chỉ'),
    ('COMMUNICATION', 'Giao tiếp hằng ngày'),
    ('WORK', 'Tiếng Anh cho công việc'),
    ('STUDY_ABROAD', 'Du học'),
    ('SCHOOL', 'Học tốt trên trường')
on conflict (purpose_code) do update
    set display_name = excluded.display_name;
