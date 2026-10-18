/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品发放已配药事件。
 */
@Getter
@Setter
public class MedicationDispensePreparedEvent extends DomainEvent {
    /** 配药人标识。 */
    private Long preparerId;
    /** 配药时间。 */
    private LocalDateTime prepareTime;

    /** 操作人/审计人标识（命令下达者）。 */
    private Long operatorId;

    /** 发放单持久化状态码（与写模型一致），供读模型投影使用。 */
    private String statusEnumCode;
    /** 批号快照（配药写入）。 */
    private String lotNumber;
    /** 追溯码快照（配药写入）。 */
    private String traceNo;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDispensePreparedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDispensePreparedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Long preparerId, LocalDateTime prepareTime逻辑。
     * @return 返回结果
     */
    public MedicationDispensePreparedEvent(Long id, Long preparerId, LocalDateTime prepareTime) {
        this(id, preparerId, prepareTime, null);
    }

    /**
     * 配药完成（含操作人/审计人）。
     *
     * @param id 发放单标识
     * @param preparerId 配药人标识
     * @param prepareTime 配药时间
     * @param operatorId 操作人标识
     */
    public MedicationDispensePreparedEvent(
        Long id, Long preparerId, LocalDateTime prepareTime, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.preparerId = preparerId;
        this.prepareTime = prepareTime;
        this.operatorId = operatorId;
    }
}
