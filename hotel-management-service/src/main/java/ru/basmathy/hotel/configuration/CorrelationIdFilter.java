package ru.basmathy.hotel.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class CorrelationIdFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Correlation-ID");
    String id =
        supplied != null && supplied.matches("[a-zA-Z0-9._-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    response.setHeader("X-Correlation-ID", id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
