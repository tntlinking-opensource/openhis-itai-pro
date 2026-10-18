/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 电子处方主表：患者历史索引 + 状态机。只写 yb_elep_*，不写 HIS 医嘱/诊断/收费。 */
@Getter
@Setter
@Entity
@Table(name = "yb_elep_prescription")
public class YbElepPrescription extends BaseAuditedEntity {

    /** 患者主键。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 就诊主键（挂号就诊，不因本方改 HIS 状态）。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 医保契约 bus_no。 */
    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    /** 院内处方号，机构内唯一。 */
    @Column(name = "hosp_rxno", nullable = false, length = 64)
    private String hospRxno;

    /** 医保处方号（A0003 回写）。 */
    @Column(name = "hi_rxno", length = 64)
    private String hiRxno;

    /** 处方追溯码（A0001 回写）。 */
    @Column(name = "rx_trace_code", length = 128)
    private String rxTraceCode;

    /** 续方源医保处方号。 */
    @Column(name = "init_rxno", length = 64)
    private String initRxno;

    /** 本地状态机。 */
    @Column(name = "local_status", nullable = false, length = 32)
    private String localStatus;

    /** 平台处方状态码。 */
    @Column(name = "rx_stas_codg", length = 8)
    private String rxStasCodg;

    /** 平台使用状态码。 */
    @Column(name = "rx_used_stas_codg", length = 8)
    private String rxUsedStasCodg;

    /** 开方时间。 */
    @Column(name = "prsc_time")
    private Instant prscTime;

    /** 开方医师姓名。 */
    @Column(name = "prsc_dr_name", length = 64)
    private String prscDrName;

    /** 开方科室名称。 */
    @Column(name = "prsc_dept_name", length = 128)
    private String prscDeptName;

    /** 主诊断医保码。 */
    @Column(name = "main_diag_code", length = 32)
    private String mainDiagCode;

    /** 主诊断名称。 */
    @Column(name = "main_diag_name", length = 256)
    private String mainDiagName;

    /** 处方类别。 */
    @Column(name = "rx_type_code", length = 8)
    private String rxTypeCode;

    /** 药品摘要。 */
    @Column(name = "drug_summary", length = 512)
    private String drugSummary;

    /** 药品行数。 */
    @Column(name = "line_count")
    private Integer lineCount;

    /** 院内处方业务号（与 hosp_rxno 对齐）。 */
    @Column(name = "prescription_no", length = 64)
    private String prescriptionNo;

    /** 复用标志。 */
    @Column(name = "rept_flag", length = 2)
    private String reptFlag;

    /** 最大复用次数。 */
    @Column(name = "max_rept_cnt")
    private Integer maxReptCnt;

    /** 已复用次数。 */
    @Column(name = "reptd_cnt")
    private Integer reptdCnt;

    /** 有效截止时间。 */
    @Column(name = "vali_end_time")
    private Instant valiEndTime;

    /** 最近一次 A0005 同步时间。 */
    @Column(name = "platform_synced_at")
    private Instant platformSyncedAt;

    /** 本方医疗类别（11/14/140104 等），慢病不写共享 map。 */
    @Column(name = "med_type", length = 16)
    private String medType;

    /** 慢病病种编码。 */
    @Column(name = "dise_codg", length = 32)
    private String diseCodg;

    /** 慢病病种名称。 */
    @Column(name = "dise_name", length = 256)
    private String diseName;

    /** 挂号 map(11) 只读缓存的医保就诊号。 */
    @Column(name = "mdtrt_id", length = 30)
    private String mdtrtId;

    /** 挂号 map(11) 只读缓存的人员编号。 */
    @Column(name = "psn_no", length = 30)
    private String psnNo;

    /** 处方有效天数。 */
    @Column(name = "vali_days", length = 8)
    private String valiDays;

    /** 审方医保药师代码。 */
    @Column(name = "phar_code", length = 32)
    private String pharCode;

    /** 审方药师姓名。 */
    @Column(name = "phar_name", length = 64)
    private String pharName;

    /** 审方药师科室医保码。 */
    @Column(name = "phar_dept_code", length = 32)
    private String pharDeptCode;

    /** 审方药师科室名称。 */
    @Column(name = "phar_dept_name", length = 128)
    private String pharDeptName;

    /** 审方药师职称编码。 */
    @Column(name = "phar_profttl_codg", length = 20)
    private String pharProfttlCodg;

    /** 审方药师执业资格证号。 */
    @Column(name = "phar_prac_cert_no", length = 64)
    private String pharPracCertNo;

    /** 院内医保药师审方时间。 */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** 审方操作人（zxplat_users.id）。 */
    @Column(name = "reviewed_by")
    private Long reviewedBy;
}
