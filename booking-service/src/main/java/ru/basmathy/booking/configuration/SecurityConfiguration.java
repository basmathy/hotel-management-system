package ru.basmathy.booking.configuration;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  SecretKeySpec jwtKey(@Value("${security.jwt.secret}") String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtDecoder jwtDecoder(
      SecretKeySpec key,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt ->
            jwt.getAudience().contains(audience)
                    && jwt.getSubject() != null
                    && jwt.getSubject().matches("[1-9][0-9]*")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, JwtAuthenticationConverter converter)
      throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));

    return http.build();
  }
}
