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

/**
 * 住院申请已发起事件。
 *
 * <p>载荷须携带读模型投影所需的状态、业务号、入院途径等字段（B.15：禁止投影侧写死状态码）。</p>
 */
@Getter
@Setter
public class InpatientAdmissionRequestedEvent extends DomainEvent {

    /** 患者标识。 */
    private Long patientId;
    /** 拟收治科室/机构标识。 */
    private Long targetDepartmentId;
    /** 源门诊或急诊就诊标识。 */
    private Long ambEncounterId;
    /** 住院就诊业务号。 */
    private String busNo;
    /** 就诊类别码（住院 {@code IMP}）。 */
    private String classEnum;
    /** 就诊状态码（申请后通常为 {@code PLANNED}）。 */
    private String statusEnum;
    /** 入院途径编码。 */
    private String inWayCode;
    /** 病情优先级编码。 */
    private String priorityEnum;
    /** 意向病区位置 id（申请项 {@code wardId}，与 {@code adm_encounter_location} 活动 WARD 一致）。 */
    private Long wardId;

    /**
     * 无参构造（序列化/框架反射用）。
     */
    public InpatientAdmissionRequestedEvent() {
        super();
    }

    /**
     * 构造住院申请事件（聚合类型为住院就诊序号域）。
     *
     * @param encounterId 新建住院就诊标识
     * @param patientId 患者标识
     * @param targetDepartmentId 拟收治科室标识
     */
    public InpatientAdmissionRequestedEvent(Long encounterId, Long patientId, Long targetDepartmentId) {
        super(encounterId, AggregateTypeConstants.INPATIENT_ENCOUNTER);
        this.patientId = patientId;
        this.targetDepartmentId = targetDepartmentId;
    }
}
