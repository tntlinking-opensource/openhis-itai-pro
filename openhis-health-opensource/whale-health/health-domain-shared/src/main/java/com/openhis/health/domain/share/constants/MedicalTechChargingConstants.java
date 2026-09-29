/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 医技工作站·医技计费（AS-MT-CHG-001）异常码常量。
 *
 * <p>仅住院耗材划价；库房须属登录 TECH 下属耗材房。</p>
 */
public interface MedicalTechChargingConstants {

    /** 选品/初始化/列表查询失败或非 TECH / 非住院（IF-MT-CHG-01/03/05）。 */
    String EX_MT_CHG_101 = "EX-MT-CHG-101";

    /** 库房非本科耗材房（IF-MT-CHG-02）。 */
    String EX_MT_CHG_201 = "EX-MT-CHG-201";

    /** 划价或冲销写失败（IF-MT-CHG-02/04）。 */
    String EX_MT_CHG_202 = "EX-MT-CHG-202";

    /** 费用已结算或不可冲销（IF-MT-CHG-04）。 */
    String EX_MT_CHG_203 = "EX-MT-CHG-203";
}
