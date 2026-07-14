/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

/**
 * 博思医保类型对照（对齐旧 OpenHis {@code EleInvoiceServiceImpl#commomSet}）。
 */
public final class BossMedicalCareMapping {

    private BossMedicalCareMapping() {
    }

    /**
     * 按险种/医保分类码解析博思医保类型。
     *
     * @param insutypeOrYbClass 险种编码；空则视为全自费
     * @return 医保类型编码与名称
     */
    public static MedCare resolve(String insutypeOrYbClass) {
        String code = insutypeOrYbClass == null ? "" : insutypeOrYbClass.trim();
        if (code.isEmpty()) {
            return new MedCare("07", "全自费");
        }
        return switch (code) {
            case "310", "320", "330", "370" -> new MedCare("01", "职工基本医疗保险");
            case "340", "360" -> new MedCare("06", "全公费");
            case "390", "392", "391", "380" -> new MedCare("02", "居民基本医疗保险");
            case "350", "410", "399" -> new MedCare("99", "其他");
            default -> new MedCare("07", "全自费");
        };
    }

    public record MedCare(String typeCode, String typeName) {
    }
}
