package com.cloudvault.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.nio.file.*;

@Service
public class StorageService {
    private final Path root;
    private final String bucket;
    private final S3Client s3;
    public StorageService(@Value("${vault.storage}") String mode, @Value("${vault.directory}") String directory,
                          @Value("${vault.bucket}") String bucket, @Value("${vault.region}") String region,
                          @Value("${vault.endpoint}") String endpoint) throws IOException {
        this.root=Path.of(directory).toAbsolutePath().normalize(); this.bucket=bucket;
        if (mode.equals("s3")) {
            if (bucket.isBlank()) throw new IllegalArgumentException("S3_BUCKET is required");
            var builder=S3Client.builder().region(Region.of(region)).httpClientBuilder(UrlConnectionHttpClient.builder());
            if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint)).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            s3=builder.build();
        } else if (mode.equals("local")) { s3=null; Files.createDirectories(root); }
        else throw new IllegalArgumentException("STORAGE_MODE must be local or s3");
    }
    private Path path(String key) { if (!key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid object key"); return root.resolve(key); }
    public void put(String key, InputStream stream, long size) {
        try {
            if(s3==null) { Path target=path(key); try { Files.copy(stream,target); } catch(IOException e) { Files.deleteIfExists(target); throw e; } }
            else s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/octet-stream").build(),RequestBody.fromInputStream(stream,size));
        } catch(IOException | RuntimeException e) { throw unavailable(e); }
    }
    public InputStream open(String key) {
        try { return s3==null?Files.newInputStream(path(key)):s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch(IOException | RuntimeException e) { throw unavailable(e); }
    }
    public void delete(String key) {
        try { if(s3==null) Files.deleteIfExists(path(key)); else s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch(IOException | RuntimeException e) { throw unavailable(e); }
    }
    private ResponseStatusException unavailable(Exception e) { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"File storage is unavailable. Try again.",e); }
    @PreDestroy public void close() { if(s3!=null) s3.close(); }
}
