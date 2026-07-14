/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 人员岗位科室查询模型，对应 {@code PractitionerRoleQueryService#listOrganizationIdsByPractitioner}。
 *
 * <p>字段契约见 SDD：DM-ADM-006-PractitionerRole-岗位角色-SDD §4.6.2。</p>
 */
@Getter
@Setter
public class PractitionerOrgIdQueryModel {
    /** 医务人员标识（adm_pr_assign.practitioner_id）。 */
    private Long practitionerId;

    /** 科室标识（adm_pr_assign.org_id）。 */
    private Long orgId;
}
