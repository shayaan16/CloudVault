package com.cloudvault.web;
import com.cloudvault.config.VaultPrincipal;
import com.cloudvault.service.VaultService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.InputStreamResource;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
@RequestMapping("/api")
public class VaultController {
    private final VaultService vault;
    public VaultController(VaultService vault){this.vault=vault;}
    record FolderBody(String name,Long parentId,Boolean quickAccess){}
    record FileBody(String name,Boolean favorite,Boolean move,Long folderId){}
    record ShareBody(String email,Integer hours){}
    @GetMapping("/folders") public List<VaultService.FolderView> folders(@AuthenticationPrincipal VaultPrincipal p){return vault.folders(p.id);}
    @PostMapping("/folders") @ResponseStatus(HttpStatus.CREATED) public void create(@AuthenticationPrincipal VaultPrincipal p,@RequestBody FolderBody b){vault.createFolder(p.id,b.parentId,b.name);}
    @PatchMapping("/folders/{id}") public void update(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestBody FolderBody b){vault.updateFolder(p.id,id,b.name,b.quickAccess);}
    @PostMapping("/folders/{id}/open") public void open(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.openFolder(p.id,id);}
    @DeleteMapping("/folders/{id}") public void deleteFolder(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.deleteFolder(p.id,id);}
    @GetMapping("/stats") public VaultService.Stats stats(@AuthenticationPrincipal VaultPrincipal p){return vault.stats(p.id);}
    @GetMapping("/files") public List<VaultService.FileView> files(@AuthenticationPrincipal VaultPrincipal p,@RequestParam(defaultValue="all") String scope,@RequestParam(required=false) Long folderId,@RequestParam(required=false) String q){return vault.files(p.id,scope,folderId,q);}
    // Serialize uploads on this single-instance deployment to enforce the global cap.
    @PostMapping("/files") @ResponseStatus(HttpStatus.CREATED) public synchronized VaultService.FileView upload(@AuthenticationPrincipal VaultPrincipal p,@RequestParam(required=false) Long folderId,@RequestPart("file") MultipartFile file){return vault.upload(p.id,folderId,file);}
    @PatchMapping("/files/{id}") public void edit(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestBody FileBody b){vault.updateFile(p.id,id,b.name,b.favorite,Boolean.TRUE.equals(b.move),b.folderId);}
    @DeleteMapping("/files/{id}") public void trash(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.trash(p.id,id);}
    @PostMapping("/files/{id}/restore") public void restore(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.restore(p.id,id);}
    @DeleteMapping("/files/{id}/permanent") public void purge(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.purge(p.id,id);}
    @DeleteMapping("/trash") public void empty(@AuthenticationPrincipal VaultPrincipal p){vault.assertWritable(p.id);for(var f:vault.files(p.id,"trash",null,null))vault.purge(p.id,f.id());}
    @PostMapping("/files/{id}/links") public VaultService.LinkResult link(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestBody ShareBody b){return vault.createLink(p.id,id,b.hours);}
    @PostMapping("/files/{id}/shares") public void direct(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestBody ShareBody b){vault.directShare(p.id,id,b.email,b.hours);}
    @GetMapping("/shares/outgoing") public List<VaultService.ShareView> outgoing(@AuthenticationPrincipal VaultPrincipal p){return vault.outgoing(p.id);}
    @GetMapping("/shares/incoming") public List<VaultService.Incoming> incoming(@AuthenticationPrincipal VaultPrincipal p){return vault.incoming(p.id);}
    @DeleteMapping("/shares/{id}") public void revoke(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id){vault.revoke(p.id,id);}
    @GetMapping("/files/{id}/download") public ResponseEntity<InputStreamResource> own(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestParam(defaultValue="false") boolean preview){return response(vault.downloadOwn(p.id,id),preview);}
    @GetMapping("/shares/{id}/download") public ResponseEntity<InputStreamResource> received(@AuthenticationPrincipal VaultPrincipal p,@PathVariable Long id,@RequestParam(defaultValue="false") boolean preview){return response(vault.downloadDirect(p.id,id),preview);}
    @GetMapping("/public/{token}") public Map<String,Object> info(@PathVariable String token){var f=vault.publicInfo(token);return Map.of("name",f.name(),"sizeBytes",f.sizeBytes(),"mimeType",f.mimeType());}
    @GetMapping("/public/{token}/download") public ResponseEntity<InputStreamResource> publicDownload(@PathVariable String token){return response(vault.downloadPublic(token),false);}
    private ResponseEntity<InputStreamResource> response(VaultService.Download d,boolean preview){
        boolean inline=preview&&(d.mimeType().equals("application/pdf")||Set.of("image/png","image/jpeg","image/gif","image/webp").contains(d.mimeType()));
        // Never serve uploaded HTML, SVG or arbitrary files as executable inline content.
        return ResponseEntity.ok().contentLength(d.size()).contentType(MediaType.parseMediaType(inline?d.mimeType():"application/octet-stream"))
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(d.name(),StandardCharsets.UTF_8).build().toString())
            .header("Content-Security-Policy","default-src 'none'; sandbox").header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(new InputStreamResource(d.stream()));
    }
}
