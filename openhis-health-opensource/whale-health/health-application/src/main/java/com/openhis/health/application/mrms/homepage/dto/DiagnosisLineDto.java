/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.homepage.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 首页诊断行 DTO。
 */
@Getter
@Setter
public class DiagnosisLineDto {

    /** 行主键。 */
    private Long id;

    /** 诊断类型码（HomepageDiagType）。 */
    private String diagTypeCode;

    /** 排序号。 */
    private Integer sortNo;

    /** ICD-10 编码。 */
    private String diagCode;

    /** 诊断名称。 */
    private String diagName;

    /** 入院病情字典码。 */
    private String admCondCode;

    /** 主诊断标记（1/0）。 */
    private String maindiagFlag;

    /** 有效标记。 */
    private String valiFlag;
}
