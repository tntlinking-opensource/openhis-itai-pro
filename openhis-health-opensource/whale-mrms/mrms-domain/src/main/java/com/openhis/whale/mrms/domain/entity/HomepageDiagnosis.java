/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.domain.AuditedEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页诊断行子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_diagnosis")
public class HomepageDiagnosis extends AuditedEntityBase {

    /** 所属病案主档主键。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户标识。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 诊断类型码，见 {@link com.openhis.whale.mrms.domain.enums.HomepageDiagType}。 */
    @Column(name = "diag_type_code", nullable = false, length = 32)
    private String diagTypeCode;

    /** 同类型内排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** ICD-10 编码。 */
    @Column(name = "diag_code", length = 32)
    private String diagCode;

    /** ICD-10 名称。 */
    @Column(name = "diag_name", length = 200)
    private String diagName;

    /** 入院病情字典码。 */
    @Column(name = "adm_cond_code", length = 16)
    private String admCondCode;

    /** 主诊断标记（4401 maindiag_flag）。 */
    @Column(name = "maindiag_flag", length = 3)
    private String maindiagFlag;

    /** 有效标记，默认 1。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
