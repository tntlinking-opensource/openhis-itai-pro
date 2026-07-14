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
 * 住院登记完成事件（DM-ADM-002 §6.1.16）。
 *
 * <p>发布时机：{@code PLANNED→REGISTERED} 或直登登记落库后；载荷含意向病区供 {@code InpatientInfo} 投影。</p>
 */
@Getter
@Setter
public class InpatientAdmissionCompletedEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 入院科室（组织）主键。 */
    private Long departmentId;

    /** 登记员（医务人员）主键。 */
    private Long registrarId;

    /** 登记后状态码（典型 {@code REGISTERED}，待护士分床后 {@code IN_PROGRESS}）。 */
    private String statusEnum;

    /** 正式入院时间。 */
    private LocalDateTime startTime;

    /** 住院业务号。 */
    private String busNo;

    /** 登记意向病区位置 id（{@code adm_encounter_location}、{@code LocationForm.WARD}）。 */
    private Long wardId;

    /** 意向病区名称快照（可选；投影缺省时由 App 位置查询回填）。 */
    private String wardName;

    /** 无参构造器（反序列化用）。 */
    public InpatientAdmissionCompletedEvent() {
        super();
    }

    public InpatientAdmissionCompletedEvent(Long encounterId, Long patientId, Long departmentId, Long registrarId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.departmentId = departmentId;
        this.registrarId = registrarId;
    }
}
