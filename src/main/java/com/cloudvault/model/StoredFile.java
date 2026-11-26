package com.cloudvault.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="vault_files", indexes={@Index(name="idx_file_owner",columnList="owner_id"), @Index(name="idx_file_folder",columnList="folder_id")})
public class StoredFile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="folder_id") public Long folderId;
    @Column(nullable=false, length=255) public String name;
    @Column(nullable=false, unique=true, length=80) public String objectKey;
    @Column(nullable=false, length=100) public String mimeType;
    public long sizeBytes;
    public boolean favorite;
    public Instant uploadedAt = Instant.now();
    public Instant deletedAt;
}
