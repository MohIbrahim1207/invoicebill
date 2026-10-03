package com.billing.invoicehub;

import com.billing.invoicehub.config.WebSocketConfig;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEC-003: Unit tests for WebSocket channel interceptor.
 *
 * Verifies that:
 * 1. ROLE_ADMIN can subscribe to /topic/admin-notifications.
 * 2. ROLE_SUPER_ADMIN can subscribe to /topic/admin-notifications.
 * 3. ROLE_VENDOR is denied when subscribing to /topic/admin-*.
 * 4. Anonymous (no principal) is denied for admin topics.
 * 5. Non-admin-prefixed topics are freely subscribed (no restriction).
 * 6. Non-SUBSCRIBE STOMP commands are unrestricted.
 */
class WebSocketSecurityInterceptorTest {

    private final WebSocketConfig config = new WebSocketConfig();

    /** Stub channel that always accepts messages. */
    private static final MessageChannel STUB_CHANNEL = new MessageChannel() {
        @Override
        public boolean send(Message<?> message) { return true; }
        @Override
        public boolean send(Message<?> message, long timeout) { return true; }
    };

    private Message<?> buildSubscribeMessage(String destination, org.springframework.security.core.Authentication auth) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (auth != null) {
            accessor.setUser(auth);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildSubscribeMessage(String destination) {
        return buildSubscribeMessage(destination, null);
    }

    private org.springframework.security.core.Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private org.springframework.security.core.Authentication superAdminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "superadmin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private org.springframework.security.core.Authentication vendorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "vendor1", null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }

    /** Capture the registered interceptor from the config. */
    private org.springframework.messaging.support.ChannelInterceptor captureInterceptor() {
        var holder = new Object() {
            org.springframework.messaging.support.ChannelInterceptor interceptor;
        };
        config.configureClientInboundChannel(new ChannelRegistration() {
            @Override
            public ChannelRegistration interceptors(org.springframework.messaging.support.ChannelInterceptor... interceptors) {
                holder.interceptor = interceptors[0];
                return this;
            }
        });
        return holder.interceptor;
    }

    @Test
    void admin_canSubscribeTo_adminNotificationsTopic() {
        var interceptor = captureInterceptor();
        Message<?> msg = buildSubscribeMessage("/topic/admin-notifications", adminAuth());
        Message<?> result = interceptor.preSend(msg, STUB_CHANNEL);
        assertThat(result).isNotNull();
    }

    @Test
    void superAdmin_canSubscribeTo_adminNotificationsTopic() {
        var interceptor = captureInterceptor();
        Message<?> msg = buildSubscribeMessage("/topic/admin-notifications", superAdminAuth());
        Message<?> result = interceptor.preSend(msg, STUB_CHANNEL);
        assertThat(result).isNotNull();
    }

    @Test
    void vendor_isDenied_fromAdminTopic() {
        var interceptor = captureInterceptor();
        Message<?> msg = buildSubscribeMessage("/topic/admin-notifications", vendorAuth());
        assertThatThrownBy(() -> interceptor.preSend(msg, STUB_CHANNEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void anonymous_isDenied_fromAdminTopic() {
        var interceptor = captureInterceptor();
        Message<?> msg = buildSubscribeMessage("/topic/admin-notifications");
        assertThatThrownBy(() -> interceptor.preSend(msg, STUB_CHANNEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void vendor_canSubscribeTo_nonAdminTopic() {
        var interceptor = captureInterceptor();
        Message<?> msg = buildSubscribeMessage("/topic/vendor-updates", vendorAuth());
        Message<?> result = interceptor.preSend(msg, STUB_CHANNEL);
        assertThat(result).isNotNull();
    }

    @Test
    void nonSubscribeCommand_isAlwaysAllowed() {
        var interceptor = captureInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, STUB_CHANNEL);
        assertThat(result).isNotNull();
    }
}
