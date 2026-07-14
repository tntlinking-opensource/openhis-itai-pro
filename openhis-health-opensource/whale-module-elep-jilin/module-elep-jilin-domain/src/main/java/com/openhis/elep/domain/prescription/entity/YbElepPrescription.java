/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 电子处方主表：患者历史索引 + 状态机。 */
@Getter
@Setter
@Entity
@Table(name = "yb_elep_prescription")
public class YbElepPrescription extends BaseAuditedEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "hosp_rxno", nullable = false, length = 64)
    private String hospRxno;

    @Column(name = "hi_rxno", length = 64)
    private String hiRxno;

    @Column(name = "rx_trace_code", length = 128)
    private String rxTraceCode;

    @Column(name = "init_rxno", length = 64)
    private String initRxno;

    @Column(name = "local_status", nullable = false, length = 32)
    private String localStatus;

    @Column(name = "rx_stas_codg", length = 8)
    private String rxStasCodg;

    @Column(name = "rx_used_stas_codg", length = 8)
    private String rxUsedStasCodg;

    @Column(name = "prsc_time")
    private Instant prscTime;

    @Column(name = "prsc_dr_name", length = 64)
    private String prscDrName;

    @Column(name = "prsc_dept_name", length = 128)
    private String prscDeptName;

    @Column(name = "main_diag_code", length = 32)
    private String mainDiagCode;

    @Column(name = "main_diag_name", length = 256)
    private String mainDiagName;

    @Column(name = "rx_type_code", length = 8)
    private String rxTypeCode;

    @Column(name = "drug_summary", length = 512)
    private String drugSummary;

    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "prescription_no", length = 64)
    private String prescriptionNo;

    @Column(name = "rept_flag", length = 2)
    private String reptFlag;

    @Column(name = "max_rept_cnt")
    private Integer maxReptCnt;

    @Column(name = "reptd_cnt")
    private Integer reptdCnt;

    @Column(name = "vali_end_time")
    private Instant valiEndTime;

    @Column(name = "platform_synced_at")
    private Instant platformSyncedAt;
}
