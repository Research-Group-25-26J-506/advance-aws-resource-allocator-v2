package app.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dev-header auth bypass — `local` profile ONLY. X-Dev-User / X-Dev-Groups headers stand in for
 * a Cognito JWT. LocalDevBypassArchTest asserts this class never rides outside the local profile.
 */
@Configuration
@EnableMethodSecurity
@Profile("local")
public class LocalDevSecurityConfig {

    @Bean
    public SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(devHeaderFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private OncePerRequestFilter devHeaderFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String user = request.getHeader("X-Dev-User");
                if (user == null) {
                    user = "dev@local";
                }
                String groups = request.getHeader("X-Dev-Groups");
                List<SimpleGrantedAuthority> authorities = Arrays.stream(
                                (groups == null ? "USER,PLATFORM_ADMIN,APPROVER,TEMPLATE_ADMIN" : groups).split(","))
                        .map(String::trim)
                        .map(g -> new SimpleGrantedAuthority("ROLE_" + g))
                        .toList();
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", authorities));
                chain.doFilter(request, response);
            }
        };
    }
}
