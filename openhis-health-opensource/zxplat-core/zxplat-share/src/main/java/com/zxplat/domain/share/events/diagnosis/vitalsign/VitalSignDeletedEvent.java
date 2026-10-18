/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.diagnosis.vitalsign;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生命体征单据已删除事件（DM-DIA-002 v2.0 §7）。
 *
 * <p>触发：{@code deleteVitalSignById}、{@code deleteVitalSignByIds}；发布时机 beforeCommit。</p>
 */
@Getter
@Setter
public class VitalSignDeletedEvent extends DomainEvent {

    /** 主表 id（批量删除时取首条）。 */
    private Long vitalSignId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 已软删主表 id 列表。 */
    private List<Long> deletedIds;

    /** 已软删明细 id 列表。 */
    private List<Long> deletedDetailIds;

    /**
     * 单据软删后发布。
     *
     * @param vitalSignId       主表 id
     * @param encounterId       就诊 id
     * @param recordedAt        记录时点
     * @param deletedIds        已删主表 id 列表
     * @param deletedDetailIds  已删明细 id 列表
     */
    public VitalSignDeletedEvent(Long vitalSignId, Long encounterId, LocalDateTime recordedAt,
        List<Long> deletedIds, List<Long> deletedDetailIds) {
        super(resolveAggregateId(vitalSignId, deletedIds), AggregateTypeConstants.VITAL_SIGN);
        this.vitalSignId = vitalSignId;
        this.encounterId = encounterId;
        this.recordedAt = recordedAt;
        this.deletedIds = deletedIds;
        this.deletedDetailIds = deletedDetailIds;
    }

    /**
     * 事件聚合标识：优先主表 id，否则取删除列表首项。
     *
     * @param vitalSignId 主表 id
     * @param deletedIds  主表 id 列表
     * @return 聚合 id
     */
    private static Long resolveAggregateId(Long vitalSignId, List<Long> deletedIds) {
        if (vitalSignId != null) {
            return vitalSignId;
        }
        if (deletedIds == null || deletedIds.isEmpty()) {
            return null;
        }
        return deletedIds.get(0);
    }
}
