/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.dto;

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

    /** 科室主键 {@code adm_organization.id}；出诊/护士科室类岗位使用。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 物理位置主键 {@code adm_location.id}；药库/药房/病区类岗位使用。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;
}
