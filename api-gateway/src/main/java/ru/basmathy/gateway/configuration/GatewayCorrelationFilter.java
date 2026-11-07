package ru.basmathy.gateway.configuration;

import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-200)
public class GatewayCorrelationFilter implements WebFilter {
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String supplied = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    String id =
        supplied != null && supplied.matches("[a-zA-Z0-9._-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    exchange.getResponse().getHeaders().set("X-Correlation-ID", id);
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              exchange.getResponse().getHeaders().set("X-Correlation-ID", id);
              return Mono.empty();
            });
    var request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  h.set("X-Correlation-ID", id);
                  h.remove("X-Internal-Token");
                })
            .build();
    return chain.filter(exchange.mutate().request(request).build());
  }
}
