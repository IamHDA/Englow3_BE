-- Preserve in-flight work submitted through the old clients before explicit component starts existed.
-- Never guess between multiple sittings that could claim the same answer.
with candidates as (
  select s.id sitting_id,'PAPER' slot,a.id attempt_id,a.started_at created_at
  from full_test_sittings s join full_tests f on f.id=s.full_test_id
  join exam_attempts a on a.user_id=s.user_id and a.exam_id=f.exam_id and a.mode='FULL' and a.status='SCORED'
  where s.status in ('IN_PROGRESS','AWAITING_GRADES') and a.started_at>=s.started_at
    and a.submitted_at<=s.deadline_at
  union all
  select s.id,v.slot,a.id,a.created_at
  from full_test_sittings s join full_tests f on f.id=s.full_test_id
  cross join lateral(values('WRITING_TASK_1',f.writing_task1_id),('WRITING_TASK_2',f.writing_task2_id),
    ('SPEAKING_PART_1',f.speaking_part1_id),('SPEAKING_PART_2',f.speaking_part2_id),('SPEAKING_PART_3',f.speaking_part3_id)) v(slot,task_id)
  join assessment_attempts a on a.user_id=s.user_id and a.task_id=v.task_id and a.status<>'DRAFT'
  where s.status in ('IN_PROGRESS','AWAITING_GRADES') and a.created_at>=s.started_at
    and a.submitted_at<=s.deadline_at
), unclaimed as (
  select c.* from candidates c where not exists(select 1 from full_test_component_attempts b
    where b.attempt_id=c.attempt_id or (b.sitting_id=c.sitting_id and b.slot=c.slot))
), ranked as (
  select c.*,row_number() over(partition by sitting_id,slot order by created_at desc,attempt_id) n,
    count(*) over(partition by attempt_id) claimants from unclaimed c
)
insert into full_test_component_attempts(id,sitting_id,slot,attempt_id)
select gen_random_uuid(),sitting_id,slot,attempt_id from ranked where n=1 and claimants=1;
