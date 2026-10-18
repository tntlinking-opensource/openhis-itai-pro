/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.query;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 住院患者药品使用行列表查询入参（AS-IP-PHA-005 · IF-IP-PMU-03）。
 *
 * <p>时间轴为 {@code planned_dispense_time}（本地日闭区间）；状态与发放类型未传时由
 * QueryService 套用 AS §1.3 默认白名单。仅本聚合 IN 下推，不跨表 EXISTS。</p>
 */
@Getter
@Setter
public class InpatientPatientMedicationUsageDispenseQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 就诊主键；必填，为空时 QueryService 返回空列表。 */
    private Long encounterId;

    /**
     * 患者主键；非空时追加 {@code patient_id} 等值（应用层交叉校验后下推，防串患）。
     */
    private Long patientId;

    /** 计划发药日下界（闭区间，含当日 00:00:00）；必填。 */
    private LocalDate plannedDispenseDateFrom;

    /** 计划发药日上界（闭区间，含当日 23:59:59.999999999）；必填。 */
    private LocalDate plannedDispenseDateTo;

    /**
     * 纳入发放状态 code 集合（{@link com.zxplat.domain.share.enums.DispenseStatus#getCode()}）；
     * 空或 null 时套用 AS-IP-PHA-005 §1.3 默认纳入白名单。
     */
    private List<String> statusEnumIn;

    /**
     * 发放类型 code 集合（{@link com.zxplat.domain.share.enums.MedicationDispenseType#getCode()}）；
     * 空或 null 时默认 {@code INPATIENT}/{@code TEMPORARY}/{@code REFUND}。
     */
    private List<String> dispenseEnumIn;
}
