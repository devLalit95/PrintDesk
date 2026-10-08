package com.example.backend.service.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.repository.PrintAgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AgentStompAuthenticationInterceptorTest {

    @Test
    void authenticatesConnectAndAllowsOnlyPrivateJobSubscription() {
        UUID agentId = UUID.randomUUID();
        JwtDecoder decoder = mock(JwtDecoder.class);
        PrintAgentRepository agents = mock(PrintAgentRepository.class);
        PrintAgentEntity agent = activeAgent(agentId, "agent-7", PrintAgentStatus.ONLINE);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        Jwt token = agentToken(agentId, "agent-7");
        when(decoder.decode("signed-token")).thenReturn(token);
        AgentStompAuthenticationInterceptor interceptor =
                new AgentStompAuthenticationInterceptor(decoder, new AgentTokenValidator(agents));
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer signed-token");
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());

        Message<?> authenticatedMessage = interceptor.preSend(connectMessage, mock(MessageChannel.class));

        assertEquals(
                "agent-7",
                StompHeaderAccessor.wrap(authenticatedMessage).getUser().getName());
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setUser(StompHeaderAccessor.wrap(authenticatedMessage).getUser());
        subscribe.setDestination("/user/queue/jobs");
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders());
        interceptor.preSend(subscribeMessage, mock(MessageChannel.class));
    }

    @Test
    void rejectsMissingCredentialsAndSharedQueueSubscriptions() {
        UUID agentId = UUID.randomUUID();
        JwtDecoder decoder = mock(JwtDecoder.class);
        PrintAgentRepository agents = mock(PrintAgentRepository.class);
        PrintAgentEntity agent = activeAgent(agentId, "agent-7", PrintAgentStatus.ONLINE);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        when(decoder.decode("signed-token")).thenReturn(agentToken(agentId, "agent-7"));
        AgentStompAuthenticationInterceptor interceptor =
                new AgentStompAuthenticationInterceptor(decoder, new AgentTokenValidator(agents));

        StompHeaderAccessor unauthenticated = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> unauthenticatedMessage =
                MessageBuilder.createMessage(new byte[0], unauthenticated.getMessageHeaders());
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(unauthenticatedMessage, mock(MessageChannel.class)));

        StompHeaderAccessor sharedSubscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sharedSubscription.setUser(new JwtAuthenticationToken(
                agentToken(agentId, "agent-7"),
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_AGENT"))));
        sharedSubscription.setDestination("/queue/jobs");
        Message<byte[]> sharedMessage =
                MessageBuilder.createMessage(new byte[0], sharedSubscription.getMessageHeaders());
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(sharedMessage, mock(MessageChannel.class)));
    }

    private PrintAgentEntity activeAgent(UUID agentId, String agentCode, PrintAgentStatus status) {
        PrintAgentEntity agent = mock(PrintAgentEntity.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getAgentCode()).thenReturn(agentCode);
        when(agent.getStatus()).thenReturn(status);
        return agent;
    }

    private Jwt agentToken(UUID agentId, String agentCode) {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(agentCode)
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("agentId", agentId.toString())
                .claim("authorities", java.util.List.of("ROLE_AGENT"))
                .build();
    }
}
