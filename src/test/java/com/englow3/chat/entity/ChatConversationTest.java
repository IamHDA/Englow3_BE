package com.englow3.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatConversationTest {

    private static final Instant FIRST = Instant.parse("2026-11-15T08:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-15T08:01:00Z");
    private static final Instant THIRD = Instant.parse("2026-11-15T08:02:00Z");

    @Test
    void theKeyOfAPairIsTheSameWhicheverSideIsAskedFirst() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(ChatConversation.directKey(a, b)).isEqualTo(ChatConversation.directKey(b, a));
    }

    @Test
    void theKeyIsTheSmallerIdThenTheLargerCompareAsText() {
        UUID smaller = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID larger = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        assertThat(ChatConversation.directKey(larger, smaller)).isEqualTo(smaller + ":" + larger);
    }

    @Test
    void theKeyFitsTheColumn() {
        assertThat(ChatConversation.directKey(UUID.randomUUID(), UUID.randomUUID()).length()).isEqualTo(73);
    }

    @Test
    void aConversationNeedsTwoDifferentPeople() {
        UUID a = UUID.randomUUID();

        assertThatThrownBy(() -> ChatConversation.directKey(a, a)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handsOutSeqOneTwoThreeAndRemembersWhenTheLastMessageWas() {
        ChatConversation conversation = new ChatConversation();
        assertThat(conversation.getLastSeq()).isZero();
        assertThat(conversation.getLastMessageAt()).isNull();

        assertThat(conversation.nextSeq(FIRST)).isEqualTo(1);
        assertThat(conversation.getLastMessageAt()).isEqualTo(FIRST);
        assertThat(conversation.nextSeq(SECOND)).isEqualTo(2);
        assertThat(conversation.nextSeq(THIRD)).isEqualTo(3);

        assertThat(conversation.getLastSeq()).isEqualTo(3);
        assertThat(conversation.getLastMessageAt()).isEqualTo(THIRD);
    }
}
