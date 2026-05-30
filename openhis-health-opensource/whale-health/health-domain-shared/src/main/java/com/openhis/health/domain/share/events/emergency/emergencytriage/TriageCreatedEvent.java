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

package com.openhis.health.domain.share.events.emergency.emergencytriage;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 分诊已创建事件。分诊记录创建成功时发布。
 */
@Getter
@Setter
public class TriageCreatedEvent extends DomainEvent {
    /** 患者标识。 */
    private Long patientId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 分诊护士标识。 */
    private Long triageNurseId;
    /** 科室标识。 */
    private Long departmentId;
    /** 机构标识。 */
    private Long organizationId;
    /** 机构名称快照。 */
    private String organizationName;
    /** 患者姓名快照。 */
    private String patientName;
    /** 患者年龄文本快照（如"45岁"）。 */
    private String patientAgeText;
    /** 患者出生日期。 */
    private LocalDate birthDate;
    /** 患者性别代码（字典）。 */
    private String patientGenderCode;
    /** 患者业务号（门诊号/住院号）。 */
    private String patientBusNo;
    /** 联系电话快照。 */
    private String phone;
    /** 证件号快照。 */
    private String idCard;
    /** 患者姓名拼音码。 */
    private String pyStr;

    /** 无参构造器（反序列化用）。 */
    public TriageCreatedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；患者快照字段通过 setter 注入。
     *
     * @param triageId     急诊分诊标识（聚合根 ID）
     * @param patientId    患者标识
     * @param encounterId  就诊标识
     * @param triageNurseId 分诊护士标识
     */
    public TriageCreatedEvent(Long triageId, Long patientId, Long encounterId, Long triageNurseId) {
        super(triageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.triageNurseId = triageNurseId;
    }
}
