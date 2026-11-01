package com.jeyz9.oasisapi.config;

import com.jeyz9.oasisapi.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import java.time.Clock;
import com.jeyz9.oasisapi.component.ApiError;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean HttpSessionSecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean AuthenticationManager authenticationManager(AccountService accounts, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain security(HttpSecurity http, HttpSessionCsrfTokenRepository csrf,
                                      HttpSessionSecurityContextRepository context, AccountService accounts, Clock clock) throws Exception {
        return http
            .csrf(c -> c.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/payments/webhooks/**"))
            .securityContext(c -> c.securityContextRepository(context))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/payments/capabilities","/api/v1/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/payments/webhooks/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sessions/**", "/api/v1/services", "/api/v1/membership-plans").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/trainer/**").hasRole("TRAINER")
                .requestMatchers("/api/v1/me/bookings/**", "/api/v1/me/memberships", "/api/v1/me/credit-ledger", "/api/v1/me/reviews", "/api/v1/me/orders/**", "/api/v1/me/receipts/**", "/api/v1/local-payment-simulator/orders/**").hasRole("CUSTOMER")
                .requestMatchers("/api/v1/local-payment-simulator/refunds/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/me").authenticated()
                .requestMatchers("/api/v1/me/notifications/**","/api/v1/me/notification-preferences").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((q,r,x) -> ApiError.write(r,401,"UNAUTHENTICATED"))
                .accessDeniedHandler((q,r,x) -> ApiError.write(r,403,"FORBIDDEN")))
            .addFilterAfter(new SessionGuard(accounts, clock), SecurityContextHolderFilter.class)
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .build();
    }
}
