/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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

    private Long patientId;
    private String outWayCode;
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
