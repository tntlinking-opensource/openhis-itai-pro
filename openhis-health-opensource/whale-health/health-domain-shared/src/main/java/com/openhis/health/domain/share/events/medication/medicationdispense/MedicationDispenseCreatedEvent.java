/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationdispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品发放单已生成事件。发放单生成（草稿或待配药状态）时发布。
 */
@Getter
@Setter
public class MedicationDispenseCreatedEvent extends DomainEvent {
    /** 药品申请标识。 */
    private Long medReqId;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 操作人标识。 */
    private Long operatorId;
    /** 发放单业务号。 */
    private String busNo;
    /** 状态枚举值（初始创建时）。 */
    private DispenseStatus statusEnum;
    /** 配药类型枚举值（临时/长期等）。 */
    private MedicationDispenseType dispenseEnum;
    /** 患者标识。 */
    private Long patientId;
    /** 科室标识（开单科室）。 */
    private Long locationId;
    /** 发放数量。 */
    private BigDecimal quantity;
    /** 单位编码（字典）。 */
    private String unitCode;
    /** 批号（签发 FIFO / 护士执行写入聚合时携带）。 */
    private String lotNumber;
    /** 处方号。 */
    private String prescriptionNo;
    /** 开单机构标识。 */
    private Long orderingOrgId;
    /** 药品申请人标识。 */
    private Long medRequesterId;

    /** 无参构造器（反序列化用）。 */
    public MedicationDispenseCreatedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；扩展字段通过 setter 注入。
     *
     * @param id          发放单标识（聚合根 ID）
     * @param medReqId    药品申请标识
     * @param medicationId 药品定义标识
     * @param encounterId 就诊标识
     * @param operatorId  操作人标识
     */
    public MedicationDispenseCreatedEvent(Long id, Long medReqId, Long medicationId,
                                          Long encounterId, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medReqId = medReqId;
        this.medicationId = medicationId;
        this.encounterId = encounterId;
        this.operatorId = operatorId;
    }
}
