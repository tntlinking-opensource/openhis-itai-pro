/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 医技工作站·费用清单（AS-MT-FEE-001）异常码常量。
 *
 * <p>就诊 scope 字符串复用 {@link MedicalTechExecutionConstants}，避免 OUTPATIENT/INPATIENT 重复落点。</p>
 */
public interface MedicalTechFeeListConstants {

    /** 查询失败或非 TECH / scope 非法（IF-MT-FEE-01/02 Gate）。 */
    String EX_MT_FEE_101 = "EX-MT-FEE-101";

    /** 打印失败或勾选行不在本科相关集（IF-MT-FEE-02）。 */
    String EX_MT_FEE_201 = "EX-MT-FEE-201";

    /** 一键确费失败（IF-MT-FEE-03）。 */
    String EX_MT_FEE_301 = "EX-MT-FEE-301";
}
