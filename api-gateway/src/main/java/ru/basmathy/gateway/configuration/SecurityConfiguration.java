package ru.basmathy.gateway.configuration;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
  @Bean
  ReactiveJwtDecoder jwtDecoder(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    var decoder =
        NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> claims =
        jwt ->
            jwt.getAudience().contains(audience)
                    && jwt.getSubject() != null
                    && jwt.getSubject().matches("[1-9][0-9]*")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), claims));
    return decoder;
  }

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http.csrf(c -> c.disable())
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            a ->
                a.pathMatchers("/internal/**")
                    .denyAll()
                    .pathMatchers("/actuator/health", "/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(
                    j ->
                        j.jwtAuthenticationConverter(
                            jwt -> {
                              List<String> roles = jwt.getClaimAsStringList("roles");
                              var authorities =
                                  (roles == null ? List.<String>of() : roles)
                                      .stream()
                                          .filter(r -> Set.of("USER", "ADMIN").contains(r))
                                          .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                          .toList();
                              return Mono.just(new JwtAuthenticationToken(jwt, authorities));
                            })))
        .build();
  }
}
