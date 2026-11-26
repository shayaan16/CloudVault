package com.cloudvault.service;
import com.cloudvault.model.*;
import com.cloudvault.repository.VaultRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Configuration
public class DemoData {
    @Bean ApplicationRunner demo(VaultRepository repo,StorageService storage,PasswordEncoder encoder,TransactionTemplate tx,@Value("${vault.demo-enabled}") boolean enabled){return args->{
        if(!enabled)return;
        tx.executeWithoutResult(status->{
            if(repo.first(Account.class,"select a from Account a where a.email=?1",AccountService.DEMO_EMAIL).isPresent())return;
            Account a=new Account();a.email=AccountService.DEMO_EMAIL;a.name="Workspace owner";a.passwordHash=encoder.encode(AccountService.DEMO_PASSWORD);a.verified=true;a.demo=true;repo.add(a);repo.flush();
            Folder documents=null;
            for(String name:List.of("Documents","Images","Audio","Project materials","Study documents")){Folder f=new Folder();f.name=name;f.ownerId=a.id;f.quickAccess=name.equals("Project materials")||name.equals("Study documents");repo.add(f);if(name.equals("Documents"))documents=f;}
            repo.flush();byte[] bytes="CloudVault demo workspace\n\nThis is a real downloadable sample file. Register an account to upload and share your own files.\n".getBytes(StandardCharsets.UTF_8);
            StoredFile file=new StoredFile();file.ownerId=a.id;file.folderId=documents.id;file.name="Welcome to CloudVault.txt";file.mimeType="text/plain";file.sizeBytes=bytes.length;file.objectKey=UUID.randomUUID().toString();file.favorite=true;
            storage.put(file.objectKey,new ByteArrayInputStream(bytes),bytes.length);repo.add(file);
        });
    };}
}
