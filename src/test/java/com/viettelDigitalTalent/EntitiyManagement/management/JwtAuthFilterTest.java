package com.viettelDigitalTalent.EntitiyManagement.management;

import com.viettelDigitalTalent.EntitiyManagement.management.filter.JwtAuthFilter;
import com.viettelDigitalTalent.EntitiyManagement.management.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private FilterChain chain;

    @InjectMocks
    private JwtAuthFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noHeader_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void headerWithoutBearer_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.isValid("bad.token")).thenReturn(false);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validToken_setsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("alice");
        when(jwtService.extractTenantId(token)).thenReturn("tenant-abc");
        when(jwtService.extractRole(token)).thenReturn("ANALYST");

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
    }

    @Test
    void validToken_detailsContainTenantId() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("bob");
        when(jwtService.extractTenantId(token)).thenReturn("my-tenant");
        when(jwtService.extractRole(token)).thenReturn("ADMIN");

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getDetails()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) auth.getDetails();
        assertThat(details.get("tenantId")).isEqualTo("my-tenant");
        assertThat(details.get("role")).isEqualTo("ADMIN");
    }

    @Test
    void validToken_chainAlwaysCalled() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.isValid("tok")).thenReturn(true);
        when(jwtService.extractUsername("tok")).thenReturn("u");
        when(jwtService.extractTenantId("tok")).thenReturn("t");
        when(jwtService.extractRole("tok")).thenReturn("ANALYST");

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
