package ru.basmathy.gateway;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "security.jwt.secret=test-signing-secret-with-at-least-32-bytes-long",
      "logging.level.root=WARN",
      "debug=false"
    })
@AutoConfigureWebTestClient(timeout = "10000")
class GatewayIntegrationTest {
  static final String SECRET = "test-signing-secret-with-at-least-32-bytes-long";
  static final AtomicReference<String> authorization = new AtomicReference<>(),
      correlation = new AtomicReference<>(),
      internal = new AtomicReference<>(),
      path = new AtomicReference<>();
  static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  static final HttpServer backend = startBackend();
  @Autowired WebTestClient client;

  static HttpServer startBackend() {
    try {
      var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.setExecutor(executor);
      server.createContext(
          "/",
          exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            internal.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            path.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange
                .getResponseHeaders()
                .set("X-Correlation-ID", exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry properties) {
    properties.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "hotel");
    properties.add(
        "spring.cloud.gateway.server.webflux.routes[0].uri",
        () -> "http://localhost:" + backend.getAddress().getPort());
    properties.add(
        "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
        () -> "Path=/api/hotels/**,/api/rooms/**");
    properties.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "booking");
    properties.add(
        "spring.cloud.gateway.server.webflux.routes[1].uri",
        () -> "http://localhost:" + backend.getAddress().getPort());
    properties.add(
        "spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
        () -> "Path=/api/auth/**,/api/users/**,/api/bookings/**");
    properties.add("spring.cloud.gateway.server.webflux.routes[2].id", () -> "unavailable-test");
    properties.add("spring.cloud.gateway.server.webflux.routes[2].uri", () -> "http://localhost:1");
    properties.add(
        "spring.cloud.gateway.server.webflux.routes[2].predicates[0]",
        () -> "Path=/api/unavailable/**");
  }

  @AfterAll
  static void stop() {
    backend.stop(0);
    executor.shutdownNow();
  }

  String token(String issuer, String audience, Instant expiration) {
    try {
      var jwt =
          new SignedJWT(
              new JWSHeader(JWSAlgorithm.HS256),
              new JWTClaimsSet.Builder()
                  .issuer(issuer)
                  .audience(audience)
                  .subject("1")
                  .claim("roles", List.of("USER"))
                  .issueTime(Date.from(Instant.now()))
                  .expirationTime(Date.from(expiration))
                  .build());
      jwt.sign(new MACSigner(SECRET));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  String token() {
    return token("hotel-platform", "hotel-api", Instant.now().plusSeconds(3600));
  }

  @Test
  void routesAndForwardsJwtAndCorrelation() throws Exception {
    String jwt = token();
    client
        .get()
        .uri("/api/hotels/7")
        .headers(h -> h.setBearerAuth(jwt))
        .header("X-Correlation-ID", "gateway-test")
        .header("X-Internal-Token", "never-forward")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Correlation-ID", "gateway-test");
    assertThat(authorization.get()).isEqualTo("Bearer " + jwt);
    assertThat(correlation.get()).isEqualTo("gateway-test");
    assertThat(internal.get()).isNull();
    assertThat(path.get()).isEqualTo("/api/hotels/7");
  }

  @Test
  void routesBookingsAndPublicAuthentication() throws Exception {
    client
        .get()
        .uri("/api/bookings")
        .headers(h -> h.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isOk();
    assertThat(path.get()).isEqualTo("/api/bookings");
    client.post().uri("/api/auth/login").exchange().expectStatus().isOk();
  }

  @Test
  void protectsPrivateEndpoints() {
    client.get().uri("/api/hotels").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void rejectsWrongIssuerAudienceAndExpiredJwt() throws Exception {
    for (String jwt :
        List.of(
            token("wrong", "hotel-api", Instant.now().plusSeconds(3600)),
            token("hotel-platform", "wrong", Instant.now().plusSeconds(3600)),
            token("hotel-platform", "hotel-api", Instant.now().minusSeconds(120))))
      client
          .get()
          .uri("/api/hotels")
          .headers(h -> h.setBearerAuth(jwt))
          .exchange()
          .expectStatus()
          .isUnauthorized();
  }

  @Test
  void internalRoutesAreNeverExposed() throws Exception {
    client
        .post()
        .uri("/internal/rooms/1/reservations")
        .headers(h -> h.setBearerAuth(token()))
        .header("X-Internal-Token", "test-internal")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void generatesSafeCorrelationId() throws Exception {
    client
        .get()
        .uri("/api/rooms")
        .headers(h -> h.setBearerAuth(token()))
        .header("X-Correlation-ID", "bad value")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueMatches("X-Correlation-ID", "[a-zA-Z0-9._-]{1,64}");
  }

  @Test
  void unavailableDestinationReturnsBoundedGatewayError() throws Exception {
    client
        .get()
        .uri("/api/unavailable/test")
        .headers(h -> h.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .value(s -> assertThat(s).isIn(503, 504))
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GATEWAY_ERROR");
  }
}
