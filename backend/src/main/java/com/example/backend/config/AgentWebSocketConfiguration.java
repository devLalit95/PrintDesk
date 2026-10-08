package com.example.backend.config;

import com.example.backend.service.agent.AgentStompAuthenticationInterceptor;
import com.example.backend.service.agent.AgentStompOutboundAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class AgentWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final AgentStompAuthenticationInterceptor authenticationInterceptor;
    private final AgentStompOutboundAuthorizationInterceptor outboundAuthorizationInterceptor;

    public AgentWebSocketConfiguration(
            AgentStompAuthenticationInterceptor authenticationInterceptor,
            AgentStompOutboundAuthorizationInterceptor outboundAuthorizationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.outboundAuthorizationInterceptor = outboundAuthorizationInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/agents");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(outboundAuthorizationInterceptor);
    }
}
