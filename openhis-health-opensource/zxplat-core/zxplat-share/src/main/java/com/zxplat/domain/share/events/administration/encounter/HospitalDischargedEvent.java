/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院出院链路事件（载荷含 {@code statusEnum}）。
 * <p>校对出院医嘱：{@code ON_HOLD}；护士办理出院：{@code PENDING}；清床离院：{@code DISCHARGED}。</p>
 */
@Getter
@Setter
public class HospitalDischargedEvent extends DomainEvent {

    /** 患者主键（HospitalDischargedEvent）。 */
    private Long patientId;
    /** outWay编码（HospitalDischargedEvent）。 */
    private String outWayCode;
    /** end时间（HospitalDischargedEvent）。 */
    private LocalDateTime endTime;
    /** 就诊状态码（{@code ON_HOLD} / {@code PENDING} / {@code DISCHARGED} 等）。 */
    private String statusEnum;
    /**
     * 出院类型（{@code ON_HOLD} 读模型「待出院」分流；取值见 {@code CustomConstants.EncounterHistoryMilestoneName#DISCHARGE}）。
     */
    private String dischargeTypeEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public HospitalDischargedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long patientId, String outWayCode, LocalDateTime endTime逻辑。
     * @return 返回结果
     */
    public HospitalDischargedEvent(Long encounterId, Long patientId, String outWayCode, LocalDateTime endTime) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.outWayCode = outWayCode;
        this.endTime = endTime;
    }
}
