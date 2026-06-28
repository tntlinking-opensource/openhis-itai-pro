/*
 * Copyright © 2026 OpenHIS Contributors
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

    /** ICD-10 诊断编码（4401 diag_code）。 */
    @Column(name = "diag_code", length = 32)
    private String diagCode;

    /** ICD-10 诊断名称（4401 diag_name）。 */
    @Column(name = "diag_name", length = 200)
    private String diagName;

    /** 入院病情字典码（4401 adm_cond，EMR 扩展回填）。 */
    @Column(name = "adm_cond_code", length = 16)
    private String admCondCode;

    /** 主诊断标记（4401 maindiag_flag）。 */
    @Column(name = "maindiag_flag", length = 3)
    private String maindiagFlag;

    /** 有效标记（4401 vali_flag，默认 1 表示有效行）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
