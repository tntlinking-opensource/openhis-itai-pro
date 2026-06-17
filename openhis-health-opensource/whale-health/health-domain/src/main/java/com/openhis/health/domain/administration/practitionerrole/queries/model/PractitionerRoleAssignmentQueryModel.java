/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 岗位分配查询模型。
 */
@Getter
@Setter
public class PractitionerRoleAssignmentQueryModel {

    private Long assignmentId;
    /** 平台角色主键 {@code whale_roles.id}。 */
    private Long roleId;
    private Long orgId;
    private Long locationId;
    private List<Long> serviceIds;
}
