package app.platform.security;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cognito JWT resource server. Groups claim (cognito:groups) maps to ROLE_* authorities used by
 * @PreAuthorize. Active in every profile EXCEPT `local` — see LocalDevSecurityConfig.
 */
@Configuration
@EnableMethodSecurity
@Profile("!local")
public class JwtSecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // pure bearer-token API: no session, no CSRF surface
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health/**", "/openapi.json", "/api/v1/webhooks/github",
                                "/api/v1/config")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoConverter())));
        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> cognitoConverter() {
        return jwt -> {
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            List<GrantedAuthority> authorities = groups == null
                    ? List.of()
                    : groups.stream()
                            .map(g -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + g))
                            .collect(Collectors.toList());
            // Access tokens carry no email claim — fall back to username, then sub, so
            // getName() (used as requester id/email) is never null.
            String name = jwt.getClaimAsString("email");
            if (name == null) {
                name = jwt.getClaimAsString("username");
            }
            if (name == null) {
                name = jwt.getSubject();
            }
            return new JwtAuthenticationToken(jwt, authorities, name);
        };
    }
}
