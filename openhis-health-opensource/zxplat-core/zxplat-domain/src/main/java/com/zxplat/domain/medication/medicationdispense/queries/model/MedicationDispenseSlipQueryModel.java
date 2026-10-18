/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发药单列表/分页查询行（替代 {@code MedicationDispenseSlipReadModel} 读侧投影）。
 */
@Getter
@Setter
public class MedicationDispenseSlipQueryModel {

    /** 发药行主键 {@code med_medication_dispense.id}。 */
    private Long dispenseId;
    /** 发药业务号。 */
    private String busNo;
    /** 关联药品医嘱主键 {@code med_medication_request.id}。 */
    private Long medReqId;
    /** 发放状态枚举。 */
    private DispenseStatus statusEnum;
    /** 未执行原因持久化码（拒发/暂停等）。 */
    private String notPerformedReasonEnum;
    /** 状态最近变更时刻。 */
    private LocalDateTime statusChangedTime;
    /** 发药类型（门诊/住院/汇总等）。 */
    private MedicationDispenseType dispenseEnum;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 发药窗口/药房位置主键。 */
    private Long locationId;
    /** 医嘱开立数量（拆零）。 */
    private BigDecimal quantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 实际发放数量（拆零）。 */
    private BigDecimal dispenseQuantity;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码（单行或汇总串）。 */
    private String traceNo;
    /** 配药人 practitioner 主键。 */
    private Long preparerId;
    /** 配药完成时刻。 */
    private LocalDateTime prepareTime;
    /** 发药人 practitioner 主键。 */
    private Long dispenserId;
    /** 发药完成时刻。 */
    private LocalDateTime dispenseTime;
    /** 汇总发药单号（住院汇总场景）。 */
    private String summaryNo;
    /** 处方号（由药嘱 {@code prescription_no} 回填；查询未 join 时可为 null）。 */
    private String prescriptionNo;
    /** 开单医生（读模型曾投影；主表无列时可为 null，由药嘱回填）。 */
    private Long medRequesterId;
    /**
     * 挂接的护士执行记录主键 {@code cln_procedure.id}。
     * <p>住院按患者发药按日分单时，用于回填 {@code recorded_time}；门诊行通常为空。</p>
     */
    private Long procedureId;
    /**
     * 计划发药/执行锚点时刻（实体 {@code planned_dispense_time}）。
     * <p>住院日分单真源为执行 {@code recorded_time}，本字段仅透出供排查，不作日键。</p>
     */
    private LocalDateTime plannedDispenseTime;
}
