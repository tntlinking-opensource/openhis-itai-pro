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
import com.openhis.whale.identity.application.dto.RoleDto;
import lombok.Data;

import java.util.List;

/**
 * 岗位人员维护详情：用户 + 医务人员 + 六类岗位分配。
 */
@Data
public class StaffDetailDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userName;
    private String name;
    private String email;
    private String phoneNumber;
    private Boolean isActive;

    private List<RoleDto> systemRoles;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    private String practitionerName;
    private String busNo;
    private String genderEnum;
    private String statusEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryOrgId;

    private List<StaffOrgLocationItemDto> doctorVisitOrgList;
    private List<StaffOrgLocationItemDto> warehouseLocationList;
    private List<StaffOrgLocationItemDto> pharmacyLocationList;
    private List<StaffOrgLocationItemDto> nurseOrgList;
    private List<StaffOrgLocationItemDto> nurseWardLocationList;

    /** 医生岗位关联的号源类型（各出诊科室岗位并集去重）。 */
    private List<Long> doctorServiceIds;
}
