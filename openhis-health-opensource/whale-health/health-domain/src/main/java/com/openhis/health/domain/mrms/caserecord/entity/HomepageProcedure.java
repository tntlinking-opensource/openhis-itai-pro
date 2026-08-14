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

/**
 * 病案首页手术操作行子实体（{@code mrms_homepage_procedure}）。
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

    /** ICD-9-CM-3 手术编码。 */
    @Column(name = "oprn_oprt_code", length = 32)
    private String oprnOprtCode;

    /** 手术/操作名称（4401 oprn_oprt_name）。 */
    @Column(name = "oprn_oprt_name", length = 200)
    private String oprnOprtName;

    /** 手术日期（4401 oprn_date）。 */
    @Column(name = "oprn_date")
    private LocalDate oprnDate;

    /** 主手术标记（4401 main_oprn_flag，1/0）。 */
    @Column(name = "main_oprn_flag", length = 3)
    private String mainOprnFlag;

    /** 手术级别字典码（4401 oprn_level）。 */
    @Column(name = "oprn_level_code", length = 16)
    private String oprnLevelCode;

    /** 切口类别字典码（4401 oprn_incision）。 */
    @Column(name = "oprn_incision_code", length = 16)
    private String oprnIncisionCode;

    /** 愈合等级字典码（4401 heal_grade）。 */
    @Column(name = "heal_grade_code", length = 16)
    private String healGradeCode;

    /** 麻醉方式字典码（4401 anst_way）。 */
    @Column(name = "anst_way_code", length = 16)
    private String anstWayCode;

    /** 术者姓名（4401 oper_dr_name）。 */
    @Column(name = "oper_dr_name", length = 50)
    private String operDrName;

    /** 术者医保医师代码（4101A oper_dr_code）。 */
    @Column(name = "oper_dr_code", length = 32)
    private String operDrCode;

    /** 麻醉医师姓名（4401 anst_dr_name）。 */
    @Column(name = "anst_dr_name", length = 50)
    private String anstDrName;

    /** 麻醉医师医保医师代码（4101A anst_dr_code）。 */
    @Column(name = "anst_dr_code", length = 32)
    private String anstDrCode;

    /** 有效标记（4401 vali_flag，默认 1 表示有效行）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
