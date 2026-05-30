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
 * 住院分床完成事件。入科分床成功时发布。
 */
@Getter
@Setter
public class InpatientBedAssignedEvent extends DomainEvent {

    /** 床位位置标识。 */
    private Long locationId;
    /** 床位号。 */
    private String bedCode;
    /** 患者标识。 */
    private Long patientId;
    /** 患者姓名快照。 */
    private String patientName;
    /** 患者性别文本快照（如"男"/"女"）。 */
    private String patientGenderText;
    /** 患者年龄文本快照（如"45岁"）。 */
    private String patientAgeText;
    /** 主管医生标识。 */
    private Long attendingDoctorId;
    /** 入院时间。 */
    private LocalDateTime admissionTime;
    /** 分床后就诊状态码（典型 {@code IN_PROGRESS}）。 */
    private String statusEnum;

    /** 无参构造器（反序列化用）。 */
    public InpatientBedAssignedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；患者快照字段通过 setter 注入。
     *
     * @param encounterId 就诊标识（聚合根 ID）
     * @param locationId  床位位置标识
     * @param bedCode     床位号
     */
    public InpatientBedAssignedEvent(Long encounterId, Long locationId, String bedCode) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.locationId = locationId;
        this.bedCode = bedCode;
    }
}
