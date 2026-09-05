package com.lexpro.lexprobackend.system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class ModelKeyCipher {
    private final String encodedKey;
    public ModelKeyCipher(@Value("${LEXPRO_MODEL_CONFIG_MASTER_KEY:}") String encodedKey) { this.encodedKey=encodedKey; }
    private SecretKeySpec key() {
        try {
            byte[] bytes=Base64.getDecoder().decode(encodedKey);
            if(bytes.length!=32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes,"AES");
        } catch(RuntimeException e) { throw new IllegalStateException("Model configuration encryption key is not configured"); }
    }
    public String encrypt(String plain) {
        try {
            var key=key();byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined=Arrays.copyOf(nonce,nonce.length+encrypted.length);System.arraycopy(encrypted,0,combined,nonce.length,encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch(Exception e) { throw new IllegalStateException("Model API key encryption failed"); }
    }
    public String decrypt(String encoded) {
        try {
            byte[] combined=Base64.getDecoder().decode(encoded);
            if(combined.length<28) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(combined,12)));
            return new String(cipher.doFinal(combined,12,combined.length-12),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new IllegalStateException("Model API key decryption failed"); }
    }
}
