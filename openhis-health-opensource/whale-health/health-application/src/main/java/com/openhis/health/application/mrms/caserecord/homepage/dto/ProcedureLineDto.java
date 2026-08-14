/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 首页手术行 DTO（含 P0 扩列：级别/切口/愈合/麻醉/术者）。
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

    /** 手术级别字典码。 */
    private String oprnLevelCode;

    /** 切口类别字典码。 */
    private String oprnIncisionCode;

    /** 愈合等级字典码。 */
    private String healGradeCode;

    /** 麻醉方式字典码。 */
    private String anstWayCode;

    /** 术者姓名。 */
    private String operDrName;

    /** 麻醉医师姓名。 */
    private String anstDrName;

    /** 有效标记。 */
    private String valiFlag;
}
