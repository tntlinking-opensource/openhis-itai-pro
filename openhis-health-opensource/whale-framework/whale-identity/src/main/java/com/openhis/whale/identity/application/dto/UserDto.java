/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户DTO
 */
@Data
public class UserDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String userName;
    private String name;
    private String surname;
    private String email;
    private String phoneNumber;
    private Boolean isActive;
    private Boolean isLockedOut;
    private LocalDateTime lockoutEnd;
    private Integer accessFailedCount;
    private Long tenantId;
    private String extraProperties;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** {@code adm_practitioner.id} */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** {@code adm_organization.id}（医务人员主档 {@code org_id}） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
