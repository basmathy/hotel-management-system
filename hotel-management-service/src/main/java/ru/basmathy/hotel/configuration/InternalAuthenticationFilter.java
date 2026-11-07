package ru.basmathy.hotel.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalAuthenticationFilter extends OncePerRequestFilter {
  private final byte[] expected;

  public InternalAuthenticationFilter(String token) {
    if (token == null || token.length() < 12)
      throw new IllegalArgumentException("Internal token must have at least 12 characters");
    expected = token.getBytes(StandardCharsets.UTF_8);
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
      throws IOException, ServletException {
    if (r.getRequestURI().substring(r.getContextPath().length()).startsWith("/internal/")) {
      String actual = r.getHeader("X-Internal-Token");
      if (actual != null
          && MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8)))
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "booking-service", null, List.of(new SimpleGrantedAuthority("INTERNAL"))));
    }
    chain.doFilter(r, s);
  }
}
