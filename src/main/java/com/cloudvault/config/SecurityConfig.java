package com.cloudvault.config;
import com.cloudvault.service.AccountService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean UserDetailsService userDetailsService(AccountService accounts,@org.springframework.beans.factory.annotation.Value("${vault.demo-enabled}") boolean demoEnabled){ return email->accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT)).filter(a->!a.demo||demoEnabled).map(VaultPrincipal::new).orElseThrow(()->new UsernameNotFoundException("Account not found")); }
    @Bean SecurityFilterChain security(HttpSecurity http,AccountService accounts) throws Exception {
        http.authorizeHttpRequests(a->a.requestMatchers("/auth.html","/share.html","/styles.css","/auth.js","/share.js","/common.js","/favicon.svg","/webjars/**","/actuator/health","/error","/api/auth/csrf","/api/auth/register","/api/auth/verify","/api/auth/resend","/api/auth/forgot","/api/auth/reset","/api/auth/login","/api/public/**").permitAll().anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{ if(req.getRequestURI().startsWith("/api/")) json(res,401,"Sign in to continue."); else res.sendRedirect("/auth.html"); }).accessDeniedHandler((req,res,ex)->json(res,403,"Access denied or session expired.")))
            .formLogin(f->f.loginPage("/auth.html").loginProcessingUrl("/api/auth/login").usernameParameter("email").successHandler((req,res,auth)->json(res,200,"Signed in.")).failureHandler((req,res,ex)->json(res,401,"Incorrect credentials or unverified email.")))
            .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID").logoutSuccessHandler((req,res,auth)->json(res,200,"Signed out.")))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; frame-src 'self' blob:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'self'"))
                .frameOptions(f->f.sameOrigin()).referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .addFilterBefore(new AttemptFilter(),UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new OncePerRequestFilter(){ @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
                var auth=SecurityContextHolder.getContext().getAuthentication();
                if(auth!=null && auth.getPrincipal() instanceof VaultPrincipal p && accounts.get(p.id).authVersion!=p.version) {
                    SecurityContextHolder.clearContext(); if(req.getSession(false)!=null) req.getSession(false).invalidate();
                } chain.doFilter(req,res);
            }},SecurityContextHolderFilter.class);
        return http.build();
    }
    static void json(HttpServletResponse res,int code,String message) throws IOException { res.setStatus(code); res.setContentType("application/json"); res.getWriter().write("{\"message\":\""+message+"\"}"); }
    // In-memory per-address throttling for a single application instance.
    static class AttemptFilter extends OncePerRequestFilter {
        private record Window(long start,int count){}
        private final Map<String,Window> windows=new ConcurrentHashMap<>();
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
            String path=req.getRequestURI();
            if(req.getMethod().equals("POST") && Set.of("/api/auth/login","/api/auth/register","/api/auth/resend","/api/auth/forgot","/api/auth/reset","/api/auth/verify").contains(path)) {
                long now=System.currentTimeMillis(); windows.entrySet().removeIf(e->now-e.getValue().start()>600000);
                if(windows.size()>10000) { json(res,429,"Too many requests. Try later."); return; }
                int max=path.equals("/api/auth/login")?20:8;
                Window w=windows.compute(req.getRemoteAddr()+path,(k,v)->v==null?new Window(now,1):new Window(v.start(),v.count()+1));
                if(w.count()>max){json(res,429,"Too many attempts. Try again in 10 minutes.");return;}
            } chain.doFilter(req,res);
        }
    }
}
