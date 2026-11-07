package ru.basmathy.booking.configuration;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  public AbstractAuthenticationToken convert(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    var authorities =
        (roles == null ? List.<String>of() : roles)
            .stream()
                .filter(r -> Set.of("USER", "ADMIN").contains(r))
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }
}
