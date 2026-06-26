package com.openhis.whale.einvoice.boss.application.boss;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 博思签名算法（MD5 大写十六进制）。
 */
public final class BossSignSupport {

    private BossSignSupport() {
    }

    /**
     * 按博思规范生成 sign：appid&data&noise + &key=...&version=1.0 → MD5 大写。
     *
     * @param appId 应用账号
     * @param dataBase64 base64 后的业务 JSON
     * @param noise 随机标识
     * @param appKey 签名私钥
     * @return 签名字符串
     */
    public static String sign(String appId, String dataBase64, String noise, String appKey) {
        Map<String, String> sorted = new TreeMap<>();
        sorted.put("appid", appId);
        sorted.put("data", dataBase64);
        sorted.put("noise", noise);
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        String stringSignTemp = builder.toString() + "&key=" + appKey + "&version=1.0";
        return md5UpperHex(stringSignTemp);
    }

    private static String md5UpperHex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(b & 0xff);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString().toUpperCase();
        } catch (Exception ex) {
            throw new IllegalStateException("Boss MD5 sign failed", ex);
        }
    }
}
