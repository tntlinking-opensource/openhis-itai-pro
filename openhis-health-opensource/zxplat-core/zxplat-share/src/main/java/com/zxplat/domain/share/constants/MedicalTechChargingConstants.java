/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 医技工作站·医技计费（AS-MT-CHG-001）异常码与文案常量。
 *
 * <p>住院医技划价：诊疗（执行科室=登录 TECH）+ 耗材（库房∈登录 TECH 下属耗材房）；
 * 来源码均为 {@code medical-tech-pricing}。</p>
 */
public interface MedicalTechChargingConstants {

    /** 选品/初始化/列表查询失败或非 TECH / 非住院（IF-MT-CHG-01/03/05/06）。 */
    String EX_MT_CHG_101 = "EX-MT-CHG-101";

    /** 库房非本科耗材房（IF-MT-CHG-02）。 */
    String EX_MT_CHG_201 = "EX-MT-CHG-201";

    /** 划价或冲销写失败（IF-MT-CHG-02/04/07）。 */
    String EX_MT_CHG_202 = "EX-MT-CHG-202";

    /** 费用已结算或不可冲销（IF-MT-CHG-04）。 */
    String EX_MT_CHG_203 = "EX-MT-CHG-203";

    /**
     * 医技划价诊疗 Procedure 完成结果文案（与护士划价 {@code 住院护士站划价完成} 分列）。
     */
    String MEDICAL_TECH_PRICING_PROCEDURE_RESULT_TEXT = "医技工作站划价完成";

    /**
     * 医技划价取消 Procedure 原因文案。
     */
    String MEDICAL_TECH_PRICING_CANCEL_PROCEDURE_REASON = "医技划价冲销";
}
