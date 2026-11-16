package com.funfriday.controller.interceptor;

import com.funfriday.service.FunFridayJwtService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

public class IpHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
    private final FunFridayJwtService jwtService;

    public IpHandshakeInterceptor(FunFridayJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            // A verified JWT wins over the readable legacy playerId cookie.
            jwtService.currentUser(servletRequest.getServletRequest())
                    .ifPresent(principal -> attributes.put("playerId", principal.playerId()));
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null && !attributes.containsKey("playerId")) {
                for (Cookie cookie : cookies) {
                    if ("playerId".equals(cookie.getName())) {
                        // Store it in attributes so @MessageMapping can see it
                        attributes.put("playerId", cookie.getValue());
                    }
                }
            }
        }
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}
