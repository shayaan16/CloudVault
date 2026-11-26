package com.cloudvault.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="vault_folders", indexes={@Index(name="idx_folder_owner",columnList="owner_id"), @Index(name="idx_folder_parent",columnList="parent_id")})
public class Folder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="parent_id") public Long parentId;
    @Column(nullable=false, length=150) public String name;
    public boolean quickAccess;
    public Instant openedAt;
    public Instant createdAt = Instant.now();
}
