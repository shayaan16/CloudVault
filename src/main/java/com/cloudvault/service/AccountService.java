package com.cloudvault.service;

import com.cloudvault.model.*;
import com.cloudvault.repository.VaultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.cloudvault.web.ApiError.*;

@Service
public class AccountService {
    private final VaultRepository repo; private final PasswordEncoder passwords; private final MailService mail;
    private final String base; private final int maxAccounts;
    public static final String DEMO_EMAIL="demo@cloudvault.example";
    public static final String DEMO_PASSWORD="CloudVaultDemo!2026";
    public AccountService(VaultRepository repo,PasswordEncoder passwords,MailService mail,@Value("${vault.base-url}") String base,@Value("${vault.max-accounts}") int maxAccounts) {
        this.repo=repo; this.passwords=passwords; this.mail=mail; this.base=base.replaceAll("/+$",""); this.maxAccounts=maxAccounts;
    }
    public static String email(String email) {
        if(email==null || email.length()>254 || !email.strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw bad("Enter a valid email address.");
        return email.strip().toLowerCase(Locale.ROOT);
    }
    public static String name(String name) {
        if(name==null || name.isBlank() || name.strip().length()>100 || name.chars().anyMatch(Character::isISOControl)) throw bad("Enter a name (1–100 characters).");
        return name.strip();
    }
    public static void validatePassword(String pass) {
        if(pass==null || pass.length()<10 || pass.getBytes(StandardCharsets.UTF_8).length>72) throw bad("Use at least 10 characters and no more than 72 UTF-8 bytes.");
    }
    @Transactional(readOnly=true) public Optional<Account> findByEmail(String email) { return repo.first(Account.class,"select a from Account a where a.email=?1",email); }
    @Transactional(readOnly=true) public Account get(Long id) { Account a=repo.find(Account.class,id); if(a==null) throw missing(); return a; }
    @Transactional public void register(String fullName,String address,String pass) {
        String email=email(address); String name=name(fullName); validatePassword(pass);
        if(email.equals(DEMO_EMAIL)) throw conflict("Email is unavailable.");
        if(findByEmail(email).isPresent()) throw conflict("Email is already registered. Try signing in or resetting your password.");
        if(repo.list(Long.class,"select count(a) from Account a where a.demo=false").getFirst()>=maxAccounts) throw conflict("Registration capacity reached.");
        Account a=new Account(); a.name=name; a.email=email; a.passwordHash=passwords.encode(pass); repo.add(a); repo.flush();
        for(String folder:List.of("Documents","Images","Audio","Project materials","Study documents")) { Folder f=new Folder(); f.ownerId=a.id; f.name=folder; f.quickAccess=folder.equals("Project materials")||folder.equals("Study documents"); repo.add(f); }
        issue(a,"verify");
    }
    @Transactional public void requestToken(String address,String purpose) {
        String email=email(address); var opt=findByEmail(email); if(opt.isEmpty()) return;
        Account a=repo.lock(Account.class,opt.get().id);
        if(a.demo || (purpose.equals("verify") && a.verified) || (purpose.equals("reset") && !a.verified)) return;
        issue(a,purpose);
    }
    private void issue(Account a,String purpose) {
        // Keep one valid token for each purpose; throttle repeated email requests.
        var previous=repo.first(AccountToken.class,"select t from AccountToken t where t.accountId=?1 and t.purpose=?2",a.id,purpose);
        long lifetime=purpose.equals("verify")?86400:1800;
        if(previous.isPresent() && previous.get().expiresAt.isAfter(Instant.now().plusSeconds(lifetime-60))) return;
        repo.execute("delete from AccountToken t where t.accountId=?1 and t.purpose=?2",a.id,purpose);
        String raw=Tokens.create(); AccountToken t=new AccountToken(); t.accountId=a.id; t.purpose=purpose; t.tokenHash=Tokens.hash(raw); t.expiresAt=Instant.now().plusSeconds(lifetime); repo.add(t); repo.flush();
        mail.send(a.email,purpose.equals("verify")?"Verify your CloudVault account":"Reset your CloudVault password", "Open this link to "+(purpose.equals("verify")?"verify your email":"reset your password")+":\n"+base+"/auth.html#"+purpose+"="+raw+"\n\nIf you did not request this, ignore this email.");
    }
    @Transactional public void redeem(String raw,String purpose,String pass) {
        if(raw==null || !raw.matches("[A-Za-z0-9_-]{43}")) throw bad("Invalid or expired link.");
        AccountToken token=repo.first(AccountToken.class,"select t from AccountToken t where t.tokenHash=?1 and t.purpose=?2",Tokens.hash(raw),purpose).orElseThrow(()->bad("Invalid or expired link."));
        Account a=repo.lock(Account.class,token.accountId);
        // Delete conditionally so a token cannot be redeemed twice, even by concurrent requests.
        if(token.expiresAt.isBefore(Instant.now()) || repo.execute("delete from AccountToken t where t.id=?1 and t.expiresAt>?2",token.id,Instant.now())!=1) throw bad("Invalid or expired link.");
        if(purpose.equals("verify")) a.verified=true;
        else { validatePassword(pass); a.passwordHash=passwords.encode(pass); a.authVersion++; }
    }
    @Transactional public void update(Long id,String name) { Account a=repo.lock(Account.class,id); writable(a); a.name=name(name); }
    @Transactional public void changePassword(Long id,String old,String next) {
        Account a=repo.lock(Account.class,id); writable(a); validatePassword(next);
        if(old==null || !passwords.matches(old,a.passwordHash)) throw bad("Current password is incorrect.");
        a.passwordHash=passwords.encode(next); a.authVersion++; repo.execute("delete from AccountToken t where t.accountId=?1 and t.purpose='reset'",id);
    }
    public static void writable(Account a) { if(a==null || a.demo) throw forbidden("The demo account is read-only. Register to make changes."); }
}
