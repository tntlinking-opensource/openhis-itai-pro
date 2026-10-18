/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.procedure;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录已创建事件。执行记录创建成功时发布。
 */
@Getter
@Setter
public class ProcedureRecordCreatedEvent extends DomainEvent {
    /** 申请单标识。 */
    private Long requestId;
    /** 申请单来源表名（区分 ServiceRequest/MedicationRequest/DeviceRequest 等）。 */
    private String requestTable;
    /** 执行状态枚举值。 */
    private String statusEnum;
    /** 执行大类枚举值。 */
    private String categoryEnum;
    /** 患者标识。 */
    private Long patientId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 执行机构标识。 */
    private Long orgId;
    /** 执行位置标识。 */
    private Long locationId;
    /** 记录人标识。 */
    private Long recorderId;
    /** 记录时间。 */
    private LocalDateTime recordedTime;
    /** 医嘱类别枚举（临时/长期等）。 */
    private String orderCategoryEnum;
    /** 医嘱优先级枚举。 */
    private String orderPriorityEnum;
    /** 医嘱申请人标识。 */
    private Long orderRequesterId;
    /** 医嘱开立时间。 */
    private LocalDateTime orderAuthoredTime;
    /** 医嘱内容 JSON（结构化快照）。 */
    private String orderContentJson;

    /** 无参构造器（反序列化用）。 */
    public ProcedureRecordCreatedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；扩展字段通过 setter 注入。
     *
     * @param procedureId  执行记录标识（聚合根 ID）
     * @param requestId    申请单标识
     * @param requestTable 申请单来源表名
     * @param statusEnum   执行状态枚举值
     */
    public ProcedureRecordCreatedEvent(Long procedureId, Long requestId, String requestTable, String statusEnum) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.requestId = requestId;
        this.requestTable = requestTable;
        this.statusEnum = statusEnum;
    }
}
