package com.google.cloud.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    return new GoogleCloudBearerTokenResolver();
  }

  @Value("${OIDC_ISSUER}")
  public String issuerUri;

  @Value("${OIDC_JWKS}")
  public String jwksUrl;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    JwtDecoder decoder1 = createJwtDecoder(issuerUri, jwksUrl);
    JwtDecoder decoder2 = createJwtDecoder("https://cloud.google.com/iap",
        "https://www.gstatic.com/iap/verify/public_key-jwk");
    JwtAuthenticationProvider provider1 = new JwtAuthenticationProvider(decoder1);
    provider1.setJwtAuthenticationConverter(new CustomJwtAuthenticationConverter());
    JwtAuthenticationProvider provider2 = new JwtAuthenticationProvider(decoder2);
    provider2.setJwtAuthenticationConverter(new CustomJwtAuthenticationConverter());
    JwtIssuerAuthenticationManagerResolver authenticationManagerResolver =
        new JwtIssuerAuthenticationManagerResolver(context -> {
          if (context.startsWith(issuerUri)) {
            return provider1::authenticate;
          } else if (context.startsWith("https://cloud.google.com/iap")) {
            return provider2::authenticate;
          } else {
            throw new RuntimeException("Unsupported Issuer " + context);
          }
        });

    http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .authenticationManagerResolver(authenticationManagerResolver)
        );
    return http.build();
  }

  private JwtDecoder createJwtDecoder(String issuer, String jwkSetUri) {
    OAuth2TokenValidator<Jwt> jwtValidator = JwtValidators.createDefaultWithIssuer(issuer);
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
        .jwsAlgorithm(SignatureAlgorithm.ES256)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    jwtDecoder.setJwtValidator(jwtValidator);
    return jwtDecoder;
  }

}
