/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 首页手术行 DTO（终改真源 {@code mrms_homepage_procedure}；对齐 AS-MRMS-HP-001 / 03 §6.1）。
 */
@Getter
@Setter
public class ProcedureLineDto {

    /** 行主键。 */
    private Long id;

    /** 排序号。 */
    private Integer sortNo;

    /** 手术操作国临编码（落库；上传出站映射 yb_no）。 */
    private String oprnOprtCode;

    /** 手术操作名称。 */
    private String oprnOprtName;

    /** 手术日期（4401；可由 oprnTime 推导）。 */
    private LocalDate oprnDate;

    /** 手术时间（业务终改；对齐临床 procedure_time；禁止与计费 occurrence_time 混用）。 */
    private LocalDateTime oprnTime;

    /** 主手术标记（1/0）。 */
    private String mainOprnFlag;

    /** 手术级别字典码。 */
    private String oprnLevelCode;

    /** 切口类别字典码。 */
    private String oprnIncisionCode;

    /** 愈合等级字典码（仅首页终改；上传→sinc_heal_lv_code）。 */
    private String healGradeCode;

    /** 麻醉方式字典码（上传→anst_mtd_code）。 */
    private String anstWayCode;

    /** 术者姓名（上传→oper_name）。 */
    private String operDrName;

    /** 一助姓名（上传→asit_1_name）。 */
    private String asit1Name;

    /** 二助姓名（上传→asit_name2）。 */
    private String asit2Name;

    /** 配合护士姓名。 */
    private String nurseName;

    /** 麻醉医师姓名。 */
    private String anstDrName;

    /** 麻醉开始时间。 */
    private LocalDateTime anstStartTime;

    /** 麻醉结束时间。 */
    private LocalDateTime anstEndTime;

    /** 样本（与备注分列）。 */
    private String specimen;

    /** 备注（与样本分列）。 */
    private String note;

    /** 是否送检。 */
    private Boolean sendLabFlag;

    /** 有效标记。 */
    private String valiFlag;
}
