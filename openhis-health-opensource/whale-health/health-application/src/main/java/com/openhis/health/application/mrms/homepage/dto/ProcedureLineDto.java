/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 首页手术行 DTO。
 */
@Getter
@Setter
public class ProcedureLineDto {

    /** 行主键。 */
    private Long id;

    /** 排序号。 */
    private Integer sortNo;

    /** 手术操作编码。 */
    private String oprnOprtCode;

    /** 手术操作名称。 */
    private String oprnOprtName;

    /** 手术日期。 */
    private LocalDate oprnDate;

    /** 主手术标记（1/0）。 */
    private String mainOprnFlag;

    /** 有效标记。 */
    private String valiFlag;
}
