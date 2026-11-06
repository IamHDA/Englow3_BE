-- The chart, graph, map or diagram an IELTS Academic Writing Task 1 describes (docs/decisions.md, D-IELTS-9). The
-- object lives in the learning bucket; the numbers the grader reads stay in rubric_notes, written from the same data
-- the chart was drawn from, so the picture the learner sees and the figures the grader checks always agree.
alter table assessment_tasks add column image_object_key varchar(500);
