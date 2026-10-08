package com.example.backend.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AgentStompOutboundAuthorizationInterceptor implements ChannelInterceptor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AgentStompOutboundAuthorizationInterceptor.class);

    private final AgentTokenValidator tokenValidator;

    public AgentStompOutboundAuthorizationInterceptor(AgentTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !(accessor.getUser() instanceof JwtAuthenticationToken authentication)) {
            return message;
        }
        try {
            tokenValidator.requireActiveAgent(authentication.getToken());
            return message;
        } catch (AccessDeniedException exception) {
            LOGGER.warn("Suppressing WebSocket delivery to an expired or revoked print agent.");
            return null;
        }
    }
}
