/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个检登记历史行。
 */
@Data
public class PersonalRegistrationHistoryRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    private String busNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    private String patientName;

    private String genderCode;

    private String phone;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageActivityId;

    private String packageName;

    private LocalDateTime registeredAt;

    private String statusEnum;

    /** 是否已收费（有 SETTLED 收费项）。 */
    private Boolean paid;

    /** 收费态未知（读收费失败时）。 */
    private Boolean chargeUnknown;
}
