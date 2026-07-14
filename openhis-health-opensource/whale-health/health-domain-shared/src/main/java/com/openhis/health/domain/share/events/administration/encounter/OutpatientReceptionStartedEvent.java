/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊接诊开始事件：携带接诊医师标识与展示名，供挂号列表、挂号单、门诊信息读模型投影。
 */
@Getter
@Setter
public class OutpatientReceptionStartedEvent extends DomainEvent {

    /** 接诊医师主键。 */
    private Long doctorId;
    /** 接诊医师姓名（展示快照）。 */
    private String doctorName;
    /** 接诊发生时刻。 */
    private LocalDateTime receptionTime;
    /** 接诊后就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致）。 */
    private String statusEnum;

    /**
     * 反序列化用无参构造。
     */
    public OutpatientReceptionStartedEvent() {
        super();
    }

    /**
     * 常用构造：标识、医师、时刻；姓名可通过 {@link #setDoctorName(String)} 在发布前补全。
     *
     * @param encounterId   就诊标识
     * @param doctorId      接诊医师主键
     * @param receptionTime 接诊时刻
     */
    public OutpatientReceptionStartedEvent(Long encounterId, Long doctorId, LocalDateTime receptionTime) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.doctorId = doctorId;
        this.receptionTime = receptionTime;
    }
}
