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

/**
 * 病案首页诊断行子实体（{@code mrms_homepage_diagnosis}）。
 *
 * <p>国临码落本表；医保对码不落本表。出院手填扩列见 Flyway
 * {@code V17__mrms_homepage_diagnosis_handfill_cols.sql}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_diagnosis")
public class HomepageDiagnosis extends AuditedEntityBase {

    /** 所属病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户主键，与病案主档 tenant_id 一致。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 诊断类型码，见 {@link com.openhis.health.domain.share.enums.HomepageDiagType}。 */
    @Column(name = "diag_type_code", nullable = false, length = 32)
    private String diagTypeCode;

    /** 同类型内排序号（4401 展示顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** 诊断编码（西医国临 / 中医目录码；院内落库真源；上传前再解析出站码）。 */
    @Column(name = "diag_code", length = 32)
    private String diagCode;

    /** 诊断名称（与 diag_code 同源目录名）。 */
    @Column(name = "diag_name", length = 200)
    private String diagName;

    /** 入院病情字典码（4401 adm_cond，EMR 扩展回填）。 */
    @Column(name = "adm_cond_code", length = 16)
    private String admCondCode;

    /**
     * 诊断转归/病情码（结算清单列「病情」；对齐老系统 outIllStatus：
     * 1治愈 2好转 3未愈 4死亡 9其他）。
     */
    @Column(name = "out_ill_status_code", length = 8)
    private String outIllStatusCode;

    /** 确诊时间（结算清单回显；手填，对齐老 confirmDate）。 */
    @Column(name = "confirm_time")
    private java.time.LocalDateTime confirmTime;

    /** 说明（结算清单回显；手填自由文本）。 */
    @Column(name = "diag_remark", length = 200)
    private String diagRemark;

    /**
     * 诊断医生主键（出院诊断下拉；对齐老系统 confirmDoctorId）。
     */
    @Column(name = "confirm_dr_id")
    private Long confirmDrId;

    /**
     * 诊断医生姓名（下拉选中后冗余；对齐老系统 confirmDoctor 展示名）。
     */
    @Column(name = "confirm_dr_name", length = 64)
    private String confirmDrName;

    /**
     * 是否上传医保（1是 0否；对齐老系统 needYbUp）。
     */
    @Column(name = "need_yb_up", length = 3)
    private String needYbUp;

    /** 主诊断标记（4401 maindiag_flag）。 */
    @Column(name = "maindiag_flag", length = 3)
    private String maindiagFlag;

    /** 有效标记（4401 vali_flag，默认 1 表示有效行）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
