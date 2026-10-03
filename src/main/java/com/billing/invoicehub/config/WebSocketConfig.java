package com.billing.invoicehub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    /** Destination prefix restricted to ROLE_ADMIN only. */
    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin-";

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // SEC-003: Restrict to the configured application origin only — no wildcards.
                .setAllowedOrigins(appBaseUrl)
                .withSockJS();
    }

    /**
     * SEC-003: Channel interceptor that enforces role-based access on STOMP subscriptions.
     * Clients subscribing to /topic/admin-* MUST be authenticated with ROLE_ADMIN or ROLE_SUPER_ADMIN.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith(ADMIN_TOPIC_PREFIX)) {
                        Principal principal = accessor.getUser();
                        if (!isAdmin(principal)) {
                            log.warn("WebSocket SUBSCRIBE denied: user '{}' attempted '{}' without ROLE_ADMIN",
                                    principal != null ? principal.getName() : "anonymous",
                                    destination);
                            throw new IllegalStateException(
                                    "Access denied: ROLE_ADMIN required to subscribe to " + destination);
                        }
                        log.debug("WebSocket SUBSCRIBE allowed for admin '{}' to '{}'",
                                principal.getName(), destination);
                    }
                }

                return message;
            }
        });
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            List<String> authorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            return authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN");
        }
        return false;
    }
}
