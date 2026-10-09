package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerLoginRateLimitTest {

    @Test
    void shouldPassRemoteAddressToAuthenticationService() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        LoginRequest request = new LoginRequest();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("198.51.100.24");

        controller.login(request, servletRequest);

        verify(authService).login(request, "198.51.100.24");
    }
}
