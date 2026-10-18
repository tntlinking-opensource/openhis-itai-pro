/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.share;

/**
 * 医保就诊凭证失效识别（电子凭证 / 身份证 SignBuff 卡密 / 社保卡卡串）。
 *
 * <p>供门诊挂号、门诊收费在 2207 失败时区分 {@code INS-015}（须重读凭证并刷新预结算）
 * 与通用 {@code INS-013}（勿重复收费）。市医保 FMI 对过期卡密常返回
 * 「解密卡串失败…已超时」，须纳入识别，避免误提示「请勿重复收费」。</p>
 */
public final class InsuranceCredentialExpirySupport {

    private InsuranceCredentialExpirySupport() {
    }

    /**
     * 判断异常链是否表明就诊凭证（含电子凭证、身份证 SignBuff、社保卡卡串）已失效。
     *
     * @param ex 2207 或平台调用抛出的异常；可为 null
     * @return 凭证过期类文案时为 true
     */
    public static boolean isExpiredCredential(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            // 任一异常层命中平台过期文案即可判定，避免只看最外层包装
            if (matchesExpiredCredentialMessage(cursor.getMessage())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 判断平台/业务文案是否表示就诊凭证已失效，须重新读卡或展码。
     *
     * @param rawMessage 异常或平台 err_msg；可为 null
     * @return 命中过期语义时为 true
     */
    public static boolean matchesExpiredCredentialMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String normalized = rawMessage.trim();
        // 电子凭证 / 通用凭证失效
        if (normalized.contains("凭证已失效")
            || normalized.contains("凭证失效")
            || normalized.contains("凭证过期")) {
            return true;
        }
        if (normalized.contains("电子凭证")
            && (normalized.contains("失效")
                || normalized.contains("过期")
                || normalized.contains("无效"))) {
            return true;
        }
        if (normalized.contains("重新展码")
            || normalized.contains("重新获取")
            || normalized.contains("ecToken")
            || normalized.contains("mdtrt_cert")) {
            return true;
        }
        // 市医保 FMI：身份证 SignBuff / 社保卡 BusiCardInfo 卡串超时
        if (normalized.contains("解密卡串")
            || normalized.contains("encodeCard")
            || normalized.contains("encodeCardReturnCardNo")) {
            return true;
        }
        if (normalized.contains("卡串")
            && (normalized.contains("超时")
                || normalized.contains("无效")
                || normalized.contains("过期")
                || normalized.contains("失效"))) {
            return true;
        }
        return false;
    }
}
