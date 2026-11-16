package com.funfriday.config;

import com.funfriday.controller.interceptor.IpHandshakeInterceptor;
import com.funfriday.service.FunFridayJwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final FunFridayJwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic is for broadcasting (Server -> Client)
        // /app is for incoming messages (Client -> Server)
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allows your Next.js port to connect
                .addInterceptors(new IpHandshakeInterceptor(jwtService))
                .withSockJS(); // This is required since you use SockJS on the frontend
    }
}
