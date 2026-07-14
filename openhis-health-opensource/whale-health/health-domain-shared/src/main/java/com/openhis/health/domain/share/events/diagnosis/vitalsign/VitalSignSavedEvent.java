/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.diagnosis.vitalsign;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生命体征单据已保存事件（DM-DIA-002 v2.0 §7）。
 *
 * <p>触发：{@code VitalSignDomainService#saveVitalSign}；发布时机 beforeCommit。</p>
 */
@Getter
@Setter
public class VitalSignSavedEvent extends DomainEvent {

    /** 主表 id。 */
    private Long vitalSignId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 单据类型 code。 */
    private String typeEnum;

    /** 本次保存后仍有效的明细 id 列表。 */
    private List<Long> savedDetailIds;

    /**
     * 主表+明细保存成功后发布。
     *
     * @param vitalSignId     主表 id
     * @param encounterId     就诊 id
     * @param patientId       患者 id
     * @param recordedAt      记录时点
     * @param typeEnum        单据类型
     * @param savedDetailIds  有效明细 id 列表
     */
    public VitalSignSavedEvent(Long vitalSignId, Long encounterId, Long patientId, LocalDateTime recordedAt,
        String typeEnum, List<Long> savedDetailIds) {
        super(vitalSignId, AggregateTypeConstants.VITAL_SIGN);
        this.vitalSignId = vitalSignId;
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.recordedAt = recordedAt;
        this.typeEnum = typeEnum;
        this.savedDetailIds = savedDetailIds;
    }
}
