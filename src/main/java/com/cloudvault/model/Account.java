package com.cloudvault.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="vault_accounts")
public class Account {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, unique=true, length=254) public String email;
    @Column(nullable=false, length=100) public String name;
    @Column(nullable=false, length=100) public String passwordHash;
    public boolean verified;
    public boolean demo;
    public long authVersion;
    public Instant createdAt = Instant.now();
}
