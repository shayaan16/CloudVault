package com.cloudvault.service;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
public final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Tokens() {}
    public static String create() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
