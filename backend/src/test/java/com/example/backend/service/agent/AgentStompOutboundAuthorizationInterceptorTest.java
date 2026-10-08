package com.example.backend.service.agent;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.repository.PrintAgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AgentStompOutboundAuthorizationInterceptorTest {

    @Test
    void suppressesDeliveryForRevokedAgent() {
        UUID agentId = UUID.randomUUID();
        PrintAgentRepository repository = mock(PrintAgentRepository.class);
        PrintAgentEntity revokedAgent = mock(PrintAgentEntity.class);
        when(repository.findById(agentId)).thenReturn(Optional.of(revokedAgent));
        when(revokedAgent.getStatus()).thenReturn(PrintAgentStatus.REVOKED);
        when(revokedAgent.getAgentCode()).thenReturn("agent-7");
        AgentStompOutboundAuthorizationInterceptor interceptor =
                new AgentStompOutboundAuthorizationInterceptor(new AgentTokenValidator(repository));
        Message<byte[]> outboundMessage = outboundMessage(agentId);

        Message<?> delivered = interceptor.preSend(outboundMessage, mock(MessageChannel.class));

        assertNull(delivered);
    }

    @Test
    void preservesDeliveryForActiveAgent() {
        UUID agentId = UUID.randomUUID();
        PrintAgentRepository repository = mock(PrintAgentRepository.class);
        PrintAgentEntity activeAgent = mock(PrintAgentEntity.class);
        when(repository.findById(agentId)).thenReturn(Optional.of(activeAgent));
        when(activeAgent.getStatus()).thenReturn(PrintAgentStatus.ONLINE);
        when(activeAgent.getAgentCode()).thenReturn("agent-7");
        AgentStompOutboundAuthorizationInterceptor interceptor =
                new AgentStompOutboundAuthorizationInterceptor(new AgentTokenValidator(repository));
        Message<byte[]> outboundMessage = outboundMessage(agentId);

        Message<?> delivered = interceptor.preSend(outboundMessage, mock(MessageChannel.class));

        assertSame(outboundMessage, delivered);
    }

    private Message<byte[]> outboundMessage(UUID agentId) {
        Jwt jwt = Jwt.withTokenValue("outbound-token")
                .header("alg", "HS256")
                .subject("agent-7")
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("agentId", agentId.toString())
                .claim("authorities", List.of("ROLE_AGENT"))
                .build();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setUser(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
