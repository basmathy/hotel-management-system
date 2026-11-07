package ru.basmathy.gateway.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.basmathy.gateway.presentation.dto.ErrorResponse;

@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {
  private final ObjectMapper json;

  public GlobalExceptionHandler(ObjectMapper json) {
    this.json = json;
  }

  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    if (exchange.getResponse().isCommitted()) return Mono.error(error);
    int status = error instanceof ResponseStatusException e ? e.getStatusCode().value() : 503;
    exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] bytes =
          json.writeValueAsBytes(
              new ErrorResponse(
                  Instant.now(),
                  status,
                  "GATEWAY_ERROR",
                  "Request could not be routed",
                  exchange.getRequest().getPath().value(),
                  exchange.getResponse().getHeaders().getFirst("X-Correlation-ID")));
      return exchange
          .getResponse()
          .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    } catch (Exception e) {
      return Mono.error(e);
    }
  }
}
