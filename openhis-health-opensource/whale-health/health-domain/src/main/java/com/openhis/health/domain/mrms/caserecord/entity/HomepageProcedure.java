/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.entity;

import com.openhis.whale.core.domain.AuditedEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 病案首页手术操作行子实体（{@code mrms_homepage_procedure}）。
 *
 * <p>手术终改真源；落库国临码；字段对齐 DM-MRMS-001 §3.2。禁止 JSON 袋承载业务字段。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_procedure")
public class HomepageProcedure extends AuditedEntityBase {

    /** 所属病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户主键，与病案主档 tenant_id 一致。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 同页内排序号（4401 展示顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** 手术国临编码（落库；上传出站映射 yb_no）。 */
    @Column(name = "oprn_oprt_code", length = 32)
    private String oprnOprtCode;

    /** 手术/操作名称。 */
    @Column(name = "oprn_oprt_name", length = 200)
    private String oprnOprtName;

    /** 手术日期（4401；可由 oprn_time 推导）。 */
    @Column(name = "oprn_date")
    private LocalDate oprnDate;

    /**
     * 手术时间（业务终改；对齐临床 procedure_time；禁止与计费 occurrence_time 混用）。
     */
    @Column(name = "oprn_time")
    private LocalDateTime oprnTime;

    /** 主手术标记（4401 main_oprn_flag，1/0）。 */
    @Column(name = "main_oprn_flag", length = 3)
    private String mainOprnFlag;

    /** 手术级别字典码。 */
    @Column(name = "oprn_level_code", length = 16)
    private String oprnLevelCode;

    /** 切口类别字典码。 */
    @Column(name = "oprn_incision_code", length = 16)
    private String oprnIncisionCode;

    /** 愈合等级字典码（仅首页终改；上传→sinc_heal_lv_code）。 */
    @Column(name = "heal_grade_code", length = 16)
    private String healGradeCode;

    /** 麻醉方式字典码（上传→anst_mtd_code）。 */
    @Column(name = "anst_way_code", length = 16)
    private String anstWayCode;

    /** 术者姓名（上传→oper_name）。 */
    @Column(name = "oper_dr_name", length = 50)
    private String operDrName;

    /** 术者医保医师代码（4101A）。 */
    @Column(name = "oper_dr_code", length = 32)
    private String operDrCode;

    /** 一助姓名。 */
    @Column(name = "asit_1_name", length = 50)
    private String asit1Name;

    /** 二助姓名（上传→asit_name2）。 */
    @Column(name = "asit_2_name", length = 50)
    private String asit2Name;

    /** 配合护士姓名。 */
    @Column(name = "nurse_name", length = 50)
    private String nurseName;

    /** 麻醉医师姓名。 */
    @Column(name = "anst_dr_name", length = 50)
    private String anstDrName;

    /** 麻醉医师医保医师代码（4101A）。 */
    @Column(name = "anst_dr_code", length = 32)
    private String anstDrCode;

    /** 麻醉开始时间。 */
    @Column(name = "anst_start_time")
    private LocalDateTime anstStartTime;

    /** 麻醉结束时间。 */
    @Column(name = "anst_end_time")
    private LocalDateTime anstEndTime;

    /** 样本（与备注分列）。 */
    @Column(name = "specimen", length = 500)
    private String specimen;

    /** 备注（与样本分列）。 */
    @Column(name = "note", length = 500)
    private String note;

    /** 是否送检。 */
    @Column(name = "send_lab_flag")
    private Boolean sendLabFlag;

    /** 有效标记（默认 1）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
