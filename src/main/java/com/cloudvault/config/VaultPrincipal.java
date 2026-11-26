package com.cloudvault.config;
import com.cloudvault.model.Account;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.List;
import java.util.Collection;
public class VaultPrincipal implements UserDetails, org.springframework.security.core.CredentialsContainer {
    public final Long id; public final long version; private final String email; private String hash; private final boolean verified,demo;
    public VaultPrincipal(Account a) { id=a.id; version=a.authVersion; email=a.email; hash=a.passwordHash; verified=a.verified; demo=a.demo; }
    @Override public String getUsername(){return email;}
    @Override public String getPassword(){return hash;}
    @Override public void eraseCredentials(){hash=null;}
    @Override public boolean isEnabled(){return verified;}
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority(demo?"ROLE_DEMO":"ROLE_USER"));}
}
