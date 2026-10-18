/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 岗位分配查询模型。
 */
@Getter
@Setter
public class PractitionerRoleAssignmentQueryModel {

    /** 岗位分配主键 {@code adm_practitioner_role.id}。 */
    private Long assignmentId;
    /** 平台角色主键 {@code zxplat_roles.id}。 */
    private Long roleId;
    /** 任职科室 {@code adm_organization.id}。 */
    private Long orgId;
    /** 任职地点/窗口 {@code adm_location.id}，可空。 */
    private Long locationId;
    /** 可提供服务项目主键集合（HealthcareService）。 */
    private List<Long> serviceIds;
}
