/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.basic.position.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 岗位人员维护：科室（{@code orgId} → {@code adm_organization.id}）或物理位置（{@code locationId} → {@code adm_location.id}）一行。
 *
 * <p>药库/药房/病区均使用 {@code locationId}；出诊/责任/护士科室等使用 {@code orgId}。</p>
 */
@Data
public class StaffOrgLocationItemDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;
}
