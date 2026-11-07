package ru.basmathy.booking.configuration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.*;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import ru.basmathy.booking.domain.model.User;

@Component
public class JwtTokenProvider {
  private final JwtEncoder encoder;
  private final String issuer;
  private final String audience;

  public JwtTokenProvider(
      SecretKeySpec key,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.issuer = issuer;
    this.audience = audience;
  }

  public String issue(User user) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getId().toString())
            .audience(List.of(audience))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("roles", List.of(user.getRole().name()))
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
