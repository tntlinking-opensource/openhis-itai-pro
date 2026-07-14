/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工伤就诊锚点（{@code yb_wi_encounter_anchor}；qualification / ipt_otp_no 真源，见 09 §5.1）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_wi_encounter_anchor")
public class YbWiEncounterAnchor extends BaseAuditedEntity {

    /** HIS 就诊 ID（唯一）。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** fin_contract.id（WORKER_INSURANCE）。 */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 1101/9102 后的人员编号。 */
    @Column(name = "psn_no", length = 30)
    private String psnNo;

    /** 9102/2301 审批资格 ID。 */
    @Column(name = "qualification_id", length = 64)
    private String qualificationId;

    /** NONE / VALID / PENDING_9102（见 WorkInjuryConstants）。 */
    @Column(name = "qualification_status", length = 16, nullable = false)
    private String qualificationStatus;

    /** 2201 门诊流水号 ipt_otp_no。 */
    @Column(name = "ipt_otp_no", length = 30)
    private String iptOtpNo;

    /** 2301 injure_affirm_id。 */
    @Column(name = "injure_affirm_id", length = 64)
    private String injureAffirmId;

    /** 入院/门诊诊断编码（2201/2206）。 */
    @Column(name = "adm_diag_code", length = 32)
    private String admDiagCode;

    /** 医疗类别 med_type。 */
    @Column(name = "med_type", length = 6)
    private String medType;

    /** 2201 submit 后为 true。 */
    @Column(name = "platform_registered")
    private Boolean platformRegistered;

    /** 是否存在未撤销 2204（决定是否插入 2205）。 */
    @Column(name = "has_active_fee_upload")
    private Boolean hasActiveFeeUpload;

    /** 2207 最新 setl_id（2208 后清空）。 */
    @Column(name = "setl_id", length = 30)
    private String setlId;
}
