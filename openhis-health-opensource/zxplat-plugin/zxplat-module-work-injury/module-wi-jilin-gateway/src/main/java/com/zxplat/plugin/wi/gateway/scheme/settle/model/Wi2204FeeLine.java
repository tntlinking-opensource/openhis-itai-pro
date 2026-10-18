/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2204】处方明细单行（inputs[] 元素，10-P0 §8.2）。
 */
@Data
@Accessors(chain = true)
public class Wi2204FeeLine {

    /** 门诊/住院流水号 ipt_otp_no。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 三大目录类别 list_type（手册：1药品/2诊疗/3材料）。 */
    @JSONField(name = "list_type")
    private String listType;

    /** 处方号 rxno。 */
    @JSONField(name = "rxno")
    private String rxno;

    /** 处方流水号 feedetl_sn；HIS chargeItem。 */
    @JSONField(name = "feedetl_sn")
    private String feedetlSn;

    /** 费用发生时间 yyyyMMddHHmmss。 */
    @JSONField(name = "fee_ocur_time")
    private String feeOcurTime;

    /** 中心目录编码 med_list_codg（院内已对照码）。 */
    @JSONField(name = "med_list_codg")
    private String medListCodg;

    /** 单价 pric。 */
    @JSONField(name = "pric")
    private String pric;

    /** 数量 cnt。 */
    @JSONField(name = "cnt")
    private String cnt;

    /** 总金额 umamt（单价×数量）。 */
    @JSONField(name = "umamt")
    private String umamt;

    /** 开单医生编码 bilg_dr_codg。 */
    @JSONField(name = "bilg_dr_codg")
    private String bilgDrCodg;

    /** 开单科室编码 bilg_dept_codg。 */
    @JSONField(name = "bilg_dept_codg")
    private String bilgDeptCodg;

    /** 是否最小计量单位；默认 "0"。 */
    @JSONField(name = "min_unit")
    private String minUnit;
}
