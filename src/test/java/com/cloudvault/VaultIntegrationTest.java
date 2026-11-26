package com.cloudvault;

import com.cloudvault.service.MailService;
import com.cloudvault.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vault;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","vault.storage=local","vault.demo-enabled=true","vault.max-accounts=100"})
@AutoConfigureMockMvc
class VaultIntegrationTest {
    static final Path directory=createTempDirectory();
    private static Path createTempDirectory(){try{return java.nio.file.Files.createTempDirectory("cloudvault-test-");}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("vault.directory",()->directory.toString());}
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @MockitoBean MailService mail;
    private static final String PASS="Password!1234";
    private String body(Object value)throws Exception{return json.writeValueAsString(value);}
    private String register(String email)throws Exception{
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(Map.of("name","Test user","email",email,"password",PASS)))).andExpect(status().isOk());
        verify(mail).send(eq(email),eq("Verify your CloudVault account"),capture.capture());
        return capture.getValue().split("#verify=")[1].split("\\s")[0];
    }
    private void verifyToken(String token)throws Exception{mvc.perform(post("/api/auth/verify").with(csrf()).contentType("application/json").content(body(Map.of("token",token)))).andExpect(status().isOk());}
    private MockHttpSession login(String email,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password",password)).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    private MockHttpSession account(String email)throws Exception{verifyToken(register(email));return login(email,PASS);}
    private long upload(MockHttpSession session,String filename,byte[] bytes)throws Exception{
        var result=mvc.perform(multipart("/api/files").file(new MockMultipartFile("file",filename,"application/octet-stream",bytes)).session(session).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void authenticationAndOneTimeReset()throws Exception{
        String email="auth@example.test";String token=register(email);
        mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password",PASS)).andExpect(status().isUnauthorized());
        verifyToken(token);mvc.perform(post("/api/auth/verify").with(csrf()).contentType("application/json").content(body(Map.of("token",token)))).andExpect(status().isBadRequest());
        MockHttpSession session=login(email,PASS);mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/forgot").with(csrf()).contentType("application/json").content(body(Map.of("email",email)))).andExpect(status().isOk());
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(eq(email),eq("Reset your CloudVault password"),capture.capture());String reset=capture.getValue().split("#reset=")[1].split("\\s")[0];
        mvc.perform(post("/api/auth/reset").with(csrf()).contentType("application/json").content(body(Map.of("token",reset,"password","NewPassword!456")))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());login(email,"NewPassword!456");
    }
    @Test void ownerIsolationSharingTrashAndQuota()throws Exception{
        var owner=account("owner@example.test");var recipient=account("reader@example.test");byte[] content="hello vault".getBytes();long id=upload(owner,"notes.txt",content);
        mvc.perform(get("/api/files/"+id+"/download").session(recipient)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/files/"+id).session(recipient).with(csrf()).contentType("application/json").content("{\"favorite\":true}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/files/"+id).session(owner).contentType("application/json").content("{\"favorite\":true}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/files/"+id).session(owner).with(csrf()).contentType("application/json").content("{\"favorite\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/files?scope=favorites").session(owner)).andExpect(jsonPath("$[0].name").value("notes.txt"));
        mvc.perform(get("/api/files/"+id+"/download").session(owner)).andExpect(content().bytes(content));
        var link=mvc.perform(post("/api/files/"+id+"/links").session(owner).with(csrf()).contentType("application/json").content("{\"hours\":1}")).andExpect(status().isOk()).andReturn();
        var result=json.readTree(link.getResponse().getContentAsString());String token=result.get("token").asText();
        mvc.perform(get("/api/public/"+token+"/download")).andExpect(content().bytes(content));
        mvc.perform(post("/api/files/"+id+"/shares").session(owner).with(csrf()).contentType("application/json").content(body(Map.of("email","reader@example.test","hours",24)))).andExpect(status().isOk());
        var incoming=mvc.perform(get("/api/shares/incoming").session(recipient)).andExpect(jsonPath("$[0].file.name").value("notes.txt")).andReturn();long share=json.readTree(incoming.getResponse().getContentAsString()).get(0).get("shareId").asLong();
        mvc.perform(get("/api/shares/"+share+"/download").session(recipient)).andExpect(content().bytes(content));
        mvc.perform(delete("/api/shares/"+result.get("id").asLong()).session(owner).with(csrf())).andExpect(status().isOk());mvc.perform(get("/api/public/"+token)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/"+id).session(owner).with(csrf())).andExpect(status().isOk());mvc.perform(get("/api/shares/"+share+"/download").session(recipient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/stats").session(owner)).andExpect(jsonPath("$.used").value(content.length)).andExpect(jsonPath("$.trash").value(1));
        mvc.perform(post("/api/files/"+id+"/restore").session(owner).with(csrf())).andExpect(status().isOk());mvc.perform(get("/api/files/"+id+"/download").session(owner)).andExpect(content().bytes(content));
        jdbc.update("update vault_files set size_bytes=? where id=?",500L*1024*1024,id);
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file","over.txt","text/plain",new byte[]{1})).session(owner).with(csrf())).andExpect(status().isConflict());
        jdbc.update("update vault_files set size_bytes=? where id=?",content.length,id);
        mvc.perform(delete("/api/files/"+id).session(owner).with(csrf())).andExpect(status().isOk());mvc.perform(delete("/api/files/"+id+"/permanent").session(owner).with(csrf())).andExpect(status().isOk());mvc.perform(get("/api/stats").session(owner)).andExpect(jsonPath("$.used").value(0));
    }
    @Test void demoCannotMutateAndAnonymousCannotRead()throws Exception{
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());var demo=login(AccountService.DEMO_EMAIL,AccountService.DEMO_PASSWORD);
        mvc.perform(get("/api/files").session(demo)).andExpect(status().isOk());mvc.perform(post("/api/folders").session(demo).with(csrf()).contentType("application/json").content("{\"name\":\"new\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file","demo.txt","text/plain",new byte[]{1})).session(demo).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void nestedFoldersAndValidation()throws Exception{
        var session=account("folder@example.test");mvc.perform(post("/api/folders").session(session).with(csrf()).contentType("application/json").content("{\"name\":\"Work\"}")).andExpect(status().isCreated());
        var response=mvc.perform(get("/api/folders").session(session)).andReturn();long id=0;for(var f:json.readTree(response.getResponse().getContentAsString()))if(f.get("name").asText().equals("Work"))id=f.get("id").asLong();assertTrue(id>0);
        mvc.perform(post("/api/folders").session(session).with(csrf()).contentType("application/json").content(body(Map.of("name","Child","parentId",id)))).andExpect(status().isCreated());
        mvc.perform(delete("/api/folders/"+id).session(session).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/folders").session(session).with(csrf()).contentType("application/json").content("{\"name\":\"../bad\"}")).andExpect(status().isBadRequest());
    }
}
