/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者建档完成事件。
 */
@Getter
@Setter
public class PatientRegisteredEvent extends DomainEvent {

    /**
     * 院内编码。
     */
    private String busNo;

    /**
     * 患者姓名。
     */
    private String name;

    /**
     * 性别编码。
     */
    private String genderEnum;

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 临时标记。
     */
    private Boolean tempFlag;

    /**
     * 建档机构标识。
     */
    private Long organizationId;

    /**
     * 无参构造函数。
     */
    public PatientRegisteredEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param busNo 院内编码
     * @param name 患者姓名
     * @param genderEnum 性别编码
     * @param statusEnum 状态编码
     * @param tempFlag 临时标记
     * @param organizationId 建档机构标识
     */
    public PatientRegisteredEvent(Long patientId, String busNo, String name, String genderEnum, String statusEnum,
                                  Boolean tempFlag, Long organizationId) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.busNo = busNo;
        this.name = name;
        this.genderEnum = genderEnum;
        this.statusEnum = statusEnum;
        this.tempFlag = tempFlag;
        this.organizationId = organizationId;
    }
}
