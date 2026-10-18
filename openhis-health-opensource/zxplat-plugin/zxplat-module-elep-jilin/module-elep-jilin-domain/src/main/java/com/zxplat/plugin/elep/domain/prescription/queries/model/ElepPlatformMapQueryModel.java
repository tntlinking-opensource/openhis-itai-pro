/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.queries.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 挂号医保登记映射只读投影（{@code yb_encounter_platform_map}，仅 med_type=11）。
 */
@Data
@Accessors(chain = true)
public class ElepPlatformMapQueryModel {

    /** 医保就诊号，A0001 {@code mdtrtId}。 */
    private String mdtrtId;

    /** 医保人员编号。 */
    private String psnNo;

    /** 门诊/住院号。 */
    private String iptOtpNo;

    /** 契约主键（只读，不回写）。 */
    private Long contractId;

    /** 医疗类别，本读取路径固定为普通门诊 {@code 11}。 */
    private String medType;
}
