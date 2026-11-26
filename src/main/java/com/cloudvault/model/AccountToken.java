package com.cloudvault.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="vault_tokens", indexes={@Index(name="idx_token_account",columnList="account_id")})
public class AccountToken {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="account_id",nullable=false) public Long accountId;
    @Column(nullable=false, unique=true, length=64) public String tokenHash;
    @Column(nullable=false, length=20) public String purpose;
    public Instant expiresAt;
}
