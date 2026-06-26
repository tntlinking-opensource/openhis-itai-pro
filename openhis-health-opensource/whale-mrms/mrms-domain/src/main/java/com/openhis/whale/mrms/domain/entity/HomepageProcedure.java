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

import java.time.LocalDate;

/**
 * 病案首页手术操作行子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_procedure")
public class HomepageProcedure extends AuditedEntityBase {

    /** 所属病案主档主键。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户标识。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** ICD-9-CM-3 手术编码。 */
    @Column(name = "oprn_oprt_code", length = 32)
    private String oprnOprtCode;

    /** 手术名称。 */
    @Column(name = "oprn_oprt_name", length = 200)
    private String oprnOprtName;

    /** 手术日期。 */
    @Column(name = "oprn_date")
    private LocalDate oprnDate;

    /** 主手术标记。 */
    @Column(name = "main_oprn_flag", length = 3)
    private String mainOprnFlag;

    /** 有效标记，默认 1。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
