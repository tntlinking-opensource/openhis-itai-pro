/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 三大目录（药品 / 诊疗 / 器材）编辑与删除业务码常量。
 *
 * <p>编辑判定：药/材先临床（{@link #LOCK_CLINICAL}）再入库（{@link #LOCK_INVENTORY}）；
 * 入库锁名称/分类/单位/拆零等，开立另锁临床段（性质/剂型/剂量单位）；规格始终可改；
 * 拆分属性与医保等级仍可改；删除判定与编辑优先级无关，见
 * {@link #isLocked}（任一业务引用即禁删）。</p>
 */
public interface CatalogEditabilityConstants {

    /** 无临床/库存业务引用，结构字段与维护态定价均可改。 */
    String OK = "OK";

    /**
     * 存在医嘱/开单等临床引用：在入库锁基础上另锁药品性质/剂型/剂量单位；
     * 规格与剂量→拆零倍数仍可改（对齐 AS-BAS-CAT-001 BR-CAT-012 / BR-CAT-013）。
     *
     * <p>编辑判定优先于 {@link #LOCK_INVENTORY}。</p>
     */
    String LOCK_CLINICAL = "LOCK_CLINICAL";

    /**
     * 存在入库事实且无临床引用（仅药/耗材）：锁定名称/分类/单位/拆零/价格等；
     * 临床段与规格仍可改；拆分属性与医保等级仍可改。
     */
    String LOCK_INVENTORY = "LOCK_INVENTORY";

    /** 无业务引用时的维护页说明。 */
    String MSG_OK = "允许编辑";

    /** 诊疗本项已开立时的维护页说明。 */
    String MSG_LOCK_CLINICAL_ACTIVITY =
        "医生开过该诊疗项目：名称、目录分类、计价单位、财务类别、医保类别、售价不可修改；业务类型仍可改";

    /**
     * 诊疗套餐子项：父项已开立时的维护页说明（开立只落父 SR，子项无独立 activity_id 引用）。
     *
     * <p>对齐 AS-BAS-CAT-001 {@code BR-CAT-014}。</p>
     */
    String MSG_LOCK_CLINICAL_PACKAGE_CHILD =
        "套餐父项已开立，本子项视为已开立：名称、目录分类、计价单位、财务类别、医保类别、售价不可修改；业务类型仍可改";

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
