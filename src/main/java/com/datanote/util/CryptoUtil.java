package com.datanote.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/CBC/PKCS5Padding 加解密工具
 * <p>
 * 加密结果格式：Base64( IV(16字节) + 密文 )
 */
public final class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private CryptoUtil() {
    }

    /**
     * 加密
     *
     * @param plainText 明文
     * @param key       16 位密钥
     * @return Base64 编码的密文（含随机 IV）
     */
    public static String encrypt(String plainText, String key) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + 密文拼接后 Base64
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    /**
     * 解密
     *
     * @param cipherText Base64 编码的密文（含 IV）
     * @param key        16 位密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String key) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }

    /**
     * 安全解密密码：null/空字符串原样返回，解密失败时回退为原文（兼容历史未加密的明文密码）
     *
     * @param password 密文或明文密码
     * @param key      16 位密钥
     * @return 解密后的明文，或解密失败时的原始值
     */
    public static String decryptSafe(String password, String key) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        try {
            return decrypt(password, key);
        } catch (Exception e) {
            // 兼容历史未加密的明文密码
            return password;
        }
    }
}
