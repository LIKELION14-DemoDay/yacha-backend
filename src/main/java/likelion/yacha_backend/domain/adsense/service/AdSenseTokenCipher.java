
package likelion.yacha_backend.domain.adsense.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdSenseTokenCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final AdSenseProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AdSenseTokenCipher(AdSenseProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new AdSenseOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "Google에서 Refresh Token을 받지 못했습니다."
            );
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key(),
                    new GCMParameterSpec(TAG_BITS, iv)
            );

            byte[] ciphertext = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            byte[] combined = ByteBuffer
                    .allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();

            return PREFIX + Base64.getEncoder()
                    .encodeToString(combined);

        } catch (GeneralSecurityException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AdSense 토큰 암호화에 실패했습니다."
            );
        }
    }

    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)
                || !encrypted.startsWith(PREFIX)) {

            throw new AdSenseOAuthException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AdSense 토큰 저장 형식이 잘못되었습니다."
            );
        }

        try {
            byte[] combined = Base64.getDecoder()
                    .decode(encrypted.substring(PREFIX.length()));

            if (combined.length < IV_LENGTH + 16) {
                throw new IllegalArgumentException(
                        "Ciphertext is too short"
                );
            }

            byte[] iv = new byte[IV_LENGTH];

            System.arraycopy(
                    combined,
                    0,
                    iv,
                    0,
                    IV_LENGTH
            );

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    new GCMParameterSpec(TAG_BITS, iv)
            );

            byte[] plaintext = cipher.doFinal(
                    combined,
                    IV_LENGTH,
                    combined.length - IV_LENGTH
            );

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException
                 | IllegalArgumentException e) {

            throw new AdSenseOAuthException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AdSense 토큰을 복호화할 수 없습니다."
            );
        }
    }

    private SecretKeySpec key() {
        if (!StringUtils.hasText(properties.tokenEncryptionKey())) {
            throw new AdSenseOAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 암호화 키가 설정되지 않았습니다."
            );
        }

        try {
            byte[] bytes = Base64.getDecoder()
                    .decode(properties.tokenEncryptionKey());

            if (bytes.length != 32) {
                throw new IllegalArgumentException(
                        "Expected 32 bytes"
                );
            }

            return new SecretKeySpec(bytes, "AES");

        } catch (IllegalArgumentException e) {
            throw new AdSenseOAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AdSense 암호화 키 형식이 잘못되었습니다."
            );
        }
    }
}
