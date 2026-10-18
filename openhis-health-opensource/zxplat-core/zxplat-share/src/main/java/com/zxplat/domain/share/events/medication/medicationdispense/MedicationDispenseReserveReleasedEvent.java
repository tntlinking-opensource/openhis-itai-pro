/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药品发放预减占用释放事件。
 *
 * <p>典型触发：医生站 FIFO 替换草稿、显式释放预减、删除未发占用等。写侧聚合仍按底座约定走 {@code markDeleted}
 *（逻辑删）；读模型 {@code MedicationDispenseSlip} 侧<strong>不</strong>再镜像逻辑删，而是由载荷中的
 * {@link #statusEnumCode}（默认 {@code ON_HOLD}）表达「暂停/释占」可视态（与《代码生成通用规范》B.15 一致）。</p>
 */
@Getter
@Setter
public class MedicationDispenseReserveReleasedEvent extends DomainEvent {
    /** 药品申请标识（与 {@link DomainEvent#getAggregateId()} 一致，供处方视图按 medReqId 加载）。 */
    private Long medReqId;
    /** 本次释放涉及的发放行条数（冗余计数，供处方备注等）。 */
    private Integer releasedCount;

    /** 操作人标识。 */
    private Long operatorId;

    /** 受影响的发放单主键列表（逐行投影）。 */
    private List<Long> releasedDispenseIds;

    /**
     * 读模型应对齐的发放状态持久化码（{@link com.zxplat.domain.share.enums.DispenseStatus#getCode()}）。
     * 发布侧固定为释占后的可视态（当前实现为 {@code ON_HOLD}）。
     */
    private String statusEnumCode;

    /** 无参构造（反序列化等）。 */
    public MedicationDispenseReserveReleasedEvent() {
        super();
    }

    /**
     * 兼容构造（仅聚合锚点 + 操作人）。
     *
     * @param id 聚合锚点
     * @param operatorId 操作人
     */
    public MedicationDispenseReserveReleasedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.operatorId = operatorId;
    }

    /**
     * 医嘱维度预减释放：聚合锚点为 {@code medReqId}，并携带被释放的发放单 ID 列表。
     *
     * @param medReqId 药品申请标识
     * @param releasedDispenseIds 被释放的发放单主键列表
     * @param operatorId 操作人标识
     */
    public MedicationDispenseReserveReleasedEvent(Long medReqId, List<Long> releasedDispenseIds, Long operatorId) {
        super(medReqId, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medReqId = medReqId;
        this.releasedDispenseIds = releasedDispenseIds;
        if (releasedDispenseIds != null) {
            this.releasedCount = releasedDispenseIds.size();
        } else {
            this.releasedCount = 0;
        }
        this.operatorId = operatorId;
    }
}
