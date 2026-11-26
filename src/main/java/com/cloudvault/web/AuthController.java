package com.cloudvault.web;
import com.cloudvault.service.AccountService;
import com.cloudvault.config.VaultPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts){this.accounts=accounts;}
    record Credentials(String name,String email,String password){}
    record Email(String email){}
    record Token(String token,String password){}
    record Profile(String name){}
    record Password(String currentPassword,String newPassword){}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"header",token.getHeaderName());}
    @PostMapping("/register") public synchronized Map<String,String> register(@RequestBody Credentials c){accounts.register(c.name,c.email,c.password);return msg("Check your inbox to verify your email.");}
    @PostMapping("/resend") public Map<String,String> resend(@RequestBody Email e){accounts.requestToken(e.email,"verify");return msg("If eligible, an email has been sent.");}
    @PostMapping("/forgot") public Map<String,String> forgot(@RequestBody Email e){accounts.requestToken(e.email,"reset");return msg("If eligible, an email has been sent.");}
    @PostMapping("/verify") public Map<String,String> verify(@RequestBody Token t){accounts.redeem(t.token,"verify",null);return msg("Email verified. You can sign in.");}
    @PostMapping("/reset") public Map<String,String> reset(@RequestBody Token t){accounts.redeem(t.token,"reset",t.password);return msg("Password changed. Sign in with your new password.");}
    @GetMapping("/me") public Map<String,Object> me(@AuthenticationPrincipal VaultPrincipal p){var a=accounts.get(p.id);return Map.of("id",a.id,"name",a.name,"email",a.email,"demo",a.demo);}
    @PatchMapping("/me") public Map<String,String> profile(@AuthenticationPrincipal VaultPrincipal p,@RequestBody Profile body){accounts.update(p.id,body.name);return msg("Profile updated.");}
    @PostMapping("/password") public Map<String,String> password(@AuthenticationPrincipal VaultPrincipal p,@RequestBody Password body){accounts.changePassword(p.id,body.currentPassword,body.newPassword);return msg("Password changed. Sign in again.");}
    private Map<String,String> msg(String message){return Map.of("message",message);}
}
