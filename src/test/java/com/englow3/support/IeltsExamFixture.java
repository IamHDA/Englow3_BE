package com.englow3.support;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;

/** A complete seven-part IELTS structure, with tiny pedagogical stand-ins for service tests. */
public final class IeltsExamFixture {
    private IeltsExamFixture() {
    }

    public static List<UUID> populate(JdbcClient jdbc, UUID examId) {
        var exams = new ExamFixture(jdbc);
        var questions = new ArrayList<UUID>();
        jdbc.sql(
                "update exams set certificate_type='IELTS',certificate_variant='ACADEMIC',duration_seconds=5520,max_raw_score=80 where id=:id")
                .param("id", examId).update();
        for (String skill : List.of("LISTENING", "READING")) {
            boolean listening = skill.equals("LISTENING");
            UUID section = exams.section(examId, listening ? 1 : 2, skill);
            jdbc.sql("update exam_sections set max_raw_score=40,time_limit_seconds=:seconds where id=:id")
                    .param("seconds", listening ? 1920 : 3600).param("id", section).update();
            int number = 0;
            for (int part = 1; part <= (listening ? 4 : 3); part++) {
                UUID partId = exams.part(section, part, "Part " + part);
                jdbc.sql("update section_parts set audio_object_key=:audio,content=:text where id=:id")
                        .param("audio", listening ? "exam-audio/fixture-part-" + part + ".mp3" : null)
                        .param("text", listening ? null : "An original reading passage for this test fixture.")
                        .param("id", partId).update();
                UUID set = exams.questionSet(partId, 1);
                int count = listening ? 10 : part == 3 ? 14 : 13;
                for (int q = 0; q < count; q++) {
                    UUID id = UUID.randomUUID();
                    jdbc.sql(
                            """
                                    insert into questions(id,question_set_id,question_type,content,difficulty_level,skill_type,order_no,max_raw_score)
                                    values(:id,:set,'TEXT_ENTRY','What is the place?','MEDIUM',:skill,:n,1)
                                    """)
                            .param("id", id).param("set", set).param("skill", skill).param("n", ++number).update();
                    jdbc.sql(
                            "insert into question_accepted_answers(id,question_id,answer_text,order_no) values(:id,:q,'museum',1)")
                            .param("id", UUID.randomUUID()).param("q", id).update();
                    questions.add(id);
                }
            }
        }
        return questions;
    }
}
