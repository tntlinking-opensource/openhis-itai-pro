/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 三大目录（药品 / 诊疗 / 器材）编辑业务码常量。
 *
 * <p>判定口径对齐 openhis-sever {@code validate*Edit}；写侧字段裁剪见各 DM §9 与
 * {@code CatalogEditabilitySupport}。</p>
 */
public interface CatalogEditabilityConstants {

    /** 无临床/库存业务引用，结构字段与维护态定价均可改。 */
    String OK = "OK";

    /** 存在医嘱/开单等临床引用（任一有效申请即锁定结构/价格/拆零字段）。 */
    String LOCK_CLINICAL = "LOCK_CLINICAL";

    /** 存在供应单审核中或已同意入库事实（在 LOCK_CLINICAL 基础上额外锁定医保等级等）。 */
    String LOCK_INVENTORY = "LOCK_INVENTORY";

    /**
     * 判断是否为已知业务码（允许进入写侧部分更新）。
     *
     * @param editabilityCode 业务码
     * @return 已知时为 true
     */
    static boolean isKnownCode(String editabilityCode) {
        if (editabilityCode == null || editabilityCode.isBlank()) {
            return false;
        }
        String code = editabilityCode.trim();
        return OK.equalsIgnoreCase(code)
            || LOCK_CLINICAL.equalsIgnoreCase(code)
            || LOCK_INVENTORY.equalsIgnoreCase(code);
    }

    /**
     * 是否处于强锁定态（存在任一业务引用）。
     *
     * @param editabilityCode 业务码
     * @return 非 OK 时为 true
     */
    static boolean isLocked(String editabilityCode) {
        if (editabilityCode == null || editabilityCode.isBlank()) {
            return false;
        }
        return !OK.equalsIgnoreCase(editabilityCode.trim());
    }
}
