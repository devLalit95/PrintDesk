package com.example.backend.service.agent;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AgentStompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String JOB_SUBSCRIPTION = "/user/queue/jobs";

    private final JwtDecoder jwtDecoder;
    private final AgentTokenValidator tokenValidator;

    public AgentStompAuthenticationInterceptor(
            JwtDecoder jwtDecoder,
            AgentTokenValidator tokenValidator) {
        this.jwtDecoder = jwtDecoder;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnect(accessor);
        } else if (accessor.getCommand() != StompCommand.DISCONNECT) {
            Authentication authentication = (Authentication) accessor.getUser();
            Jwt jwt = requireJwt(authentication);
            tokenValidator.requireActiveAgent(jwt);
            if (accessor.getCommand() == StompCommand.SUBSCRIBE
                    && !JOB_SUBSCRIPTION.equals(accessor.getDestination())) {
                throw new AccessDeniedException("The agent may subscribe only to its private job destination.");
            }
            if (accessor.getCommand() == StompCommand.SEND) {
                throw new AccessDeniedException("The agent connection accepts server-to-agent notifications only.");
            }
        }
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= "Bearer ".length()) {
            throw new AccessDeniedException("A bearer token is required to connect the print agent.");
        }
        Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()).trim());
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        if (authorities == null || !authorities.contains("ROLE_AGENT")) {
            throw new AccessDeniedException("An agent token is required to connect.");
        }
        tokenValidator.requireActiveAgent(jwt);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        accessor.setUser(authentication);
    }

    private Jwt requireJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        throw new AccessDeniedException("The agent connection is not authenticated.");
    }

}
