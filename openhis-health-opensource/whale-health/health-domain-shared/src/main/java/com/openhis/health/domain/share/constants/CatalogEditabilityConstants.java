/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 三大目录（药品 / 诊疗 / 器材）编辑与删除业务码常量。
 *
 * <p>编辑判定：药/材先临床（{@link #LOCK_CLINICAL}）再入库（{@link #LOCK_INVENTORY}）；
 * 入库锁名称/分类/单位/拆零等，开立另锁临床与规格；拆分属性与医保等级仍可改；删除判定与编辑优先级无关，见
 * {@link #isLocked}（任一业务引用即禁删）。</p>
 */
public interface CatalogEditabilityConstants {

    /** 无临床/库存业务引用，结构字段与维护态定价均可改。 */
    String OK = "OK";

    /**
     * 存在医嘱/开单等临床引用：在入库锁基础上另锁药品性质/规格/剂型/剂量单位与倍数；
     * 拆分属性与医保等级仍可改。
     *
     * <p>编辑判定优先于 {@link #LOCK_INVENTORY}。</p>
     */
    String LOCK_CLINICAL = "LOCK_CLINICAL";

    /**
     * 存在入库事实且无临床引用（仅药/耗材）：锁定名称/分类/单位/拆零/价格等；
     * 临床与规格段仍可改；拆分属性与医保等级仍可改。
     */
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
     * <p>删除门禁用此方法：临床或入库任一命中即禁删（整单不能删），与编辑「先二级后一级」
     * 的业务码优先级无关。</p>
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
