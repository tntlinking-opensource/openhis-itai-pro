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

    /** 有效标记（4401 vali_flag，默认 1 表示有效行）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
