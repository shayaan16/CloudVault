package com.cloudvault.service;

import com.cloudvault.model.*;
import com.cloudvault.repository.VaultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static com.cloudvault.web.ApiError.*;

@Service
public class VaultService {
    public static final long FILE_LIMIT=50L*1024*1024, USER_LIMIT=500L*1024*1024;
    private final VaultRepository repo; private final StorageService storage; private final long globalLimit;
    public VaultService(VaultRepository repo, StorageService storage,@Value("${vault.global-limit}") long globalLimit){this.repo=repo;this.storage=storage;this.globalLimit=globalLimit;}
    public record FileView(Long id,Long folderId,String name,String mimeType,long sizeBytes,boolean favorite,Instant uploadedAt,Instant deletedAt){}
    public record FolderView(Long id,Long parentId,String name,boolean quickAccess,Instant openedAt,long fileCount){}
    public record ShareView(Long id,Long fileId,String fileName,String recipient,String kind,Instant expiresAt,boolean expired){}
    public record Incoming(Long shareId,String owner,FileView file,Instant expiresAt){}
    public record Stats(long used,long limit,long documents,long media,long other,long count,long trash){}
    public record Download(String name,String mimeType,long size,InputStream stream){}
    public record LinkResult(Long id,String token,Instant expiresAt){}
    private FileView view(StoredFile f){return new FileView(f.id,f.folderId,f.name,f.mimeType,f.sizeBytes,f.favorite,f.uploadedAt,f.deletedAt);}
    private Account lockUser(Long id){Account a=repo.lock(Account.class,id);AccountService.writable(a);return a;}
    private Folder folder(Long owner,Long id){Folder f=repo.find(Folder.class,id);if(f==null || !f.ownerId.equals(owner))throw missing();return f;}
    private StoredFile owned(Long owner,Long id){StoredFile f=repo.find(StoredFile.class,id);if(f==null || !f.ownerId.equals(owner))throw missing();return f;}
    private StoredFile active(Long owner,Long id){StoredFile f=owned(owner,id);if(f.deletedAt!=null)throw missing();return f;}
    public static String validName(String name,int max){
        if(name==null || name.isBlank() || name.strip().length()>max || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl) || name.strip().equals(".") || name.strip().equals("..")) throw bad("Invalid name (no slashes or control characters).");
        return name.strip();
    }
    private void uniqueFolder(Long owner,Long parent,String name,Long except){
        boolean exists=repo.list(Folder.class,"select f from Folder f where f.ownerId=?1",owner).stream().anyMatch(f->Objects.equals(f.parentId,parent)&&f.name.equalsIgnoreCase(name)&&!Objects.equals(f.id,except));
        if(exists)throw conflict("A folder with that name already exists.");
    }
    @Transactional(readOnly=true) public List<FolderView> folders(Long owner){
        var all=repo.list(Folder.class,"select f from Folder f where f.ownerId=?1 order by f.id",owner);
        var files=repo.list(StoredFile.class,"select f from StoredFile f where f.ownerId=?1 and f.deletedAt is null",owner);
        return all.stream().map(f->new FolderView(f.id,f.parentId,f.name,f.quickAccess,f.openedAt,files.stream().filter(x->Objects.equals(x.folderId,f.id)).count())).toList();
    }
    @Transactional public void createFolder(Long owner,Long parent,String name){
        lockUser(owner);if(parent!=null)folder(owner,parent);name=validName(name,150);uniqueFolder(owner,parent,name,null);
        if(repo.list(Long.class,"select count(f) from Folder f where f.ownerId=?1",owner).getFirst()>=200)throw conflict("Folder limit reached (200).");
        Folder f=new Folder();f.ownerId=owner;f.parentId=parent;f.name=name;repo.add(f);
    }
    @Transactional public void updateFolder(Long owner,Long id,String name,Boolean quick){lockUser(owner);Folder f=folder(owner,id);if(name!=null){name=validName(name,150);uniqueFolder(owner,f.parentId,name,id);f.name=name;}if(quick!=null)f.quickAccess=quick;}
    @Transactional public void openFolder(Long owner,Long id){Folder f=folder(owner,id);if(!repo.find(Account.class,owner).demo)f.openedAt=Instant.now();}
    @Transactional public void deleteFolder(Long owner,Long id){
        lockUser(owner);Folder f=folder(owner,id);
        if(!repo.list(Folder.class,"select f from Folder f where f.parentId=?1",id).isEmpty()||!repo.list(StoredFile.class,"select f from StoredFile f where f.folderId=?1",id).isEmpty())throw conflict("Move or permanently remove all files (including trash) and subfolders first.");repo.remove(f);
    }
    @Transactional(readOnly=true) public List<FileView> files(Long owner,String scope,Long folderId,String query){
        if(folderId!=null)folder(owner,folderId);String q=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        if(!Set.of("all","folder","recent","favorites","trash","root").contains(scope))throw bad("Invalid scope.");
        return repo.list(StoredFile.class,"select f from StoredFile f where f.ownerId=?1 order by f.uploadedAt desc",owner).stream()
            .filter(f->scope.equals("trash")?f.deletedAt!=null:f.deletedAt==null)
            .filter(f->!scope.equals("favorites")||f.favorite).filter(f->!scope.equals("folder")||Objects.equals(f.folderId,folderId)).filter(f->!scope.equals("root")||f.folderId==null)
            .filter(f->f.name.toLowerCase(Locale.ROOT).contains(q)).map(this::view).toList();
    }
    @Transactional(readOnly=true) public Stats stats(Long owner){
        long docs=0,media=0,other=0,count=0,trash=0;
        for(StoredFile f:repo.list(StoredFile.class,"select f from StoredFile f where f.ownerId=?1",owner)){
            if(f.deletedAt!=null)trash++;else count++;
            if(f.mimeType.startsWith("image/")||f.mimeType.startsWith("audio/")||f.mimeType.startsWith("video/"))media+=f.sizeBytes;
            else if(f.mimeType.equals("application/pdf")||f.mimeType.startsWith("text/")||f.name.toLowerCase(Locale.ROOT).matches(".*\\.(docx?|xlsx?|pptx?|odt|ods|csv)$"))docs+=f.sizeBytes;
            else other+=f.sizeBytes;
        }return new Stats(docs+media+other,USER_LIMIT,docs,media,other,count,trash);
    }
    private long sum(String query,Object...args){Long result=repo.list(Long.class,query,args).getFirst();return result==null?0:result;}
    @Transactional public FileView upload(Long owner,Long folderId,MultipartFile part){
        lockUser(owner);if(folderId!=null)folder(owner,folderId);long size=part.getSize();
        if(size<=0||size>FILE_LIMIT)throw bad("Choose a non-empty file up to 50 MB.");
        if(sum("select sum(f.sizeBytes) from StoredFile f where f.ownerId=?1",owner)+size>USER_LIMIT)throw conflict("Your 500 MB storage limit is reached. Empty the recycle bin to free space.");
        if(sum("select sum(f.sizeBytes) from StoredFile f")+size>globalLimit)throw conflict("Server storage capacity reached.");
        if(repo.list(Long.class,"select count(f) from StoredFile f where f.ownerId=?1",owner).getFirst()>=1000)throw conflict("File count limit reached (1000).");
        StoredFile f=new StoredFile();f.ownerId=owner;f.folderId=folderId;f.name=validName(part.getOriginalFilename(),255);f.sizeBytes=size;f.objectKey=UUID.randomUUID().toString();
        try(BufferedInputStream in=new BufferedInputStream(part.getInputStream())){
            in.mark(32);byte[] head=in.readNBytes(16);in.reset();f.mimeType=detect(head,f.name);
            storage.put(f.objectKey,in,size);
        }catch(IOException ex){throw bad("Cannot read uploaded file.");}
        String key=f.objectKey;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(key);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(VaultService.class).warn("Could not clean up failed upload {}",key);}}});
        repo.add(f);repo.flush();return view(f);
    }
    static String detect(byte[] h,String name){
        if(h.length>=5 && h[0]=='%'&&h[1]=='P'&&h[2]=='D'&&h[3]=='F'&&h[4]=='-')return "application/pdf";
        if(h.length>=8 && (h[0]&255)==137&&h[1]=='P'&&h[2]=='N'&&h[3]=='G'&&h[4]==13&&h[5]==10&&h[6]==26&&h[7]==10)return "image/png";
        if(h.length>=3&&(h[0]&255)==255&&(h[1]&255)==216&&(h[2]&255)==255)return "image/jpeg";
        if(h.length>=6 && new String(h,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a"))return "image/gif";
        if(h.length>=12 && new String(h,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(h,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        String n=name.toLowerCase(Locale.ROOT);if(n.endsWith(".txt")||n.endsWith(".md")||n.endsWith(".csv"))return "text/plain";
        if(n.endsWith(".mp3")||n.endsWith(".wav"))return "audio/mpeg";if(n.endsWith(".mp4"))return "video/mp4";
        return "application/octet-stream";
    }
    @Transactional public void updateFile(Long owner,Long id,String name,Boolean favorite,boolean move,Long folderId){
        lockUser(owner);StoredFile f=active(owner,id);if(name!=null)f.name=validName(name,255);if(favorite!=null)f.favorite=favorite;if(move){if(folderId!=null)folder(owner,folderId);f.folderId=folderId;}
    }
    @Transactional public void trash(Long owner,Long id){lockUser(owner);StoredFile f=active(owner,id);f.deletedAt=Instant.now();repo.execute("delete from Share s where s.fileId=?1",id);}
    @Transactional public void restore(Long owner,Long id){lockUser(owner);StoredFile f=owned(owner,id);if(f.deletedAt==null)throw bad("File is not in the recycle bin.");f.deletedAt=null;}
    @Transactional(readOnly=true) public void assertWritable(Long owner){AccountService.writable(repo.find(Account.class,owner));}
    @Transactional public void purge(Long owner,Long id){lockUser(owner);StoredFile f=owned(owner,id);if(f.deletedAt==null)throw bad("Move the file to the recycle bin first.");storage.delete(f.objectKey);repo.remove(f);}
    private Instant expiry(Integer hours){int h=hours==null?24:hours;if(h<1||h>168)throw bad("Expiry must be 1–168 hours.");return Instant.now().plusSeconds(h*3600L);}
    private void shareLimit(Long owner){if(repo.list(Long.class,"select count(s) from Share s where s.ownerId=?1",owner).getFirst()>=200)throw conflict("Share limit reached. Revoke old links first.");}
    @Transactional public LinkResult createLink(Long owner,Long file,Integer hours){lockUser(owner);active(owner,file);shareLimit(owner);Share s=new Share();s.ownerId=owner;s.fileId=file;s.expiresAt=expiry(hours);String raw=Tokens.create();s.tokenHash=Tokens.hash(raw);repo.add(s);repo.flush();return new LinkResult(s.id,raw,s.expiresAt);}
    @Transactional public void directShare(Long owner,Long file,String email,Integer hours){
        lockUser(owner);active(owner,file);shareLimit(owner);Account recipient=repo.first(Account.class,"select a from Account a where a.email=?1 and a.verified=true and a.demo=false",AccountService.email(email)).orElseThrow(()->bad("Recipient must be a verified registered user."));
        if(recipient.id.equals(owner))throw bad("Cannot share with yourself.");
        repo.execute("delete from Share s where s.fileId=?1 and s.recipientId=?2",file,recipient.id);
        Share s=new Share();s.fileId=file;s.ownerId=owner;s.recipientId=recipient.id;s.expiresAt=expiry(hours);repo.add(s);
    }
    @Transactional(readOnly=true) public List<ShareView> outgoing(Long owner){return repo.list(Share.class,"select s from Share s where s.ownerId=?1 order by s.createdAt desc",owner).stream().map(s->new ShareView(s.id,s.fileId,repo.find(StoredFile.class,s.fileId).name,s.recipientId==null?null:repo.find(Account.class,s.recipientId).email,s.tokenHash==null?"User":"Link",s.expiresAt,!s.expiresAt.isAfter(Instant.now()))).toList();}
    @Transactional(readOnly=true) public List<Incoming> incoming(Long owner){return repo.list(Share.class,"select s from Share s where s.recipientId=?1 and s.expiresAt>?2 order by s.createdAt desc",owner,Instant.now()).stream().map(s->new Incoming(s.id,repo.find(Account.class,s.ownerId).name,view(active(s.ownerId,s.fileId)),s.expiresAt)).toList();}
    @Transactional public void revoke(Long owner,Long id){lockUser(owner);Share s=repo.find(Share.class,id);if(s==null||!s.ownerId.equals(owner))throw missing();repo.remove(s);}
    private StoredFile directFile(Long recipient,Long id){Share s=repo.find(Share.class,id);if(s==null||!Objects.equals(s.recipientId,recipient)||!s.expiresAt.isAfter(Instant.now()))throw missing();return active(s.ownerId,s.fileId);}
    private StoredFile publicFile(String raw){if(raw==null||!raw.matches("[A-Za-z0-9_-]{43}"))throw missing();Share s=repo.first(Share.class,"select s from Share s where s.tokenHash=?1 and s.expiresAt>?2",Tokens.hash(raw),Instant.now()).orElseThrow(()->missing());return active(s.ownerId,s.fileId);}
    @Transactional(readOnly=true) public FileView publicInfo(String raw){return view(publicFile(raw));}
    @Transactional(readOnly=true) public Download downloadOwn(Long owner,Long id){return download(active(owner,id));}
    @Transactional(readOnly=true) public Download downloadDirect(Long owner,Long share){return download(directFile(owner,share));}
    @Transactional(readOnly=true) public Download downloadPublic(String token){return download(publicFile(token));}
    private Download download(StoredFile f){return new Download(f.name,f.mimeType,f.sizeBytes,storage.open(f.objectKey));}
}
