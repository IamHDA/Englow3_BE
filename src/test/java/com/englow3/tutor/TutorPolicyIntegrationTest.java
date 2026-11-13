package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.*;
import com.englow3.tutor.dto.command.*;
import com.englow3.tutor.service.*;

class TutorPolicyIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorPolicyService policy;
    @Autowired
    TutorService tutor;
    @Autowired
    TutorReplyWriter writer;
    @Autowired
    JdbcClient jdbc;
    private Object previousScope;
    private UUID user;

    @BeforeEach
    void setup() {
        previousScope = ReflectionTestUtils.getField(policy, "scope");
        ReflectionTestUtils.setField(policy, "scope", "policy-test-" + UUID.randomUUID());
        ReflectionTestUtils.setField(tutor, "enabled", true);
        user = new LearnerFixture(jdbc).learner();
        as("ADMIN");
    }

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(policy, "scope", previousScope);
        ReflectionTestUtils.setField(tutor, "enabled", false);
        SignedIn.out();
    }

    private void as(String role) {
        SignedIn.as(jdbc, user);
        var token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token.getToken(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void pauseRejectsNewWorkButPreservesReceiptsAndHistory() {
        var key = UUID.randomUUID();
        var command = new SendTutorMessageCommand(null, "Synthetic policy question", null, key, "GENERAL", false);
        var sent = tutor.send(command);
        var before = policy.view();
        var saved = policy.update(
                new TutorPolicyCommand(false, List.of("GENERAL"), before.version(), "Pause during maintenance"));
        assertThat(saved.version()).isEqualTo(before.version() + 1);
        assertThat(tutor.usage().enabled()).isFalse();
        assertThat(tutor.conversation(sent.conversation().id()).messages()).hasSize(2);
        assertThat(tutor.send(command).conversation().id()).isEqualTo(sent.conversation().id());
        assertThatThrownBy(() -> tutor.send(new SendTutorMessageCommand(null, "Another question", null)))
                .hasMessageContaining("unavailable");
        assertThatThrownBy(() -> policy.update(
                new TutorPolicyCommand(true, List.of("GENERAL"), before.version(), "An outdated settings change")))
                        .hasMessageContaining("Reload");
    }

    @Test
    void aDifferentPayloadModeCannotBypassTheStoredConversationPolicy() {
        var sent = tutor.send(new SendTutorMessageCommand(null, "Synthetic policy follow-up", null, UUID.randomUUID(),
                "GENERAL", false));
        writer.storeReply(sent.messages().get(1).id(), 0, "Synthetic explanation", "test", 1, 1);
        policy.update(new TutorPolicyCommand(true, List.of("CORRECTION"), 0,
                "Pause general explanations during maintenance"));
        assertThatThrownBy(() -> tutor.send(new SendTutorMessageCommand(sent.conversation().id(), "Explain more", null,
                UUID.randomUUID(), "CORRECTION", false))).hasMessageContaining("mode");
    }

    @Test
    void onlyAdminCanConfigureAndDisabledModesCannotEnqueue() {
        as("STAFF");
        assertThatThrownBy(() -> policy.view()).hasMessageContaining("Administrator");
        assertThatThrownBy(() -> policy.update(new TutorPolicyCommand(false, List.of(), 0, "Unauthorized change")))
                .hasMessageContaining("Administrator");
        as("ADMIN");
        policy.update(new TutorPolicyCommand(true, List.of("CORRECTION"), 0, "Only sentence correction enabled"));
        assertThatThrownBy(() -> tutor
                .send(new SendTutorMessageCommand(null, "Explain articles", null, UUID.randomUUID(), "GENERAL", false)))
                        .hasMessageContaining("mode");
        assertThatThrownBy(() -> policy
                .update(new TutorPolicyCommand(true, java.util.Arrays.asList((String) null), 1, "Invalid mode input")))
                        .hasMessageContaining("supported");
        assertThat(tutor.usage().allowedModes()).containsExactly("CORRECTION");
    }
}
