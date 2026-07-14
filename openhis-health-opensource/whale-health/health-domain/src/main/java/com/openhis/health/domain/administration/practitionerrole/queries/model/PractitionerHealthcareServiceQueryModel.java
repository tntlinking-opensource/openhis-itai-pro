/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 人员+科室下可用就诊类型查询模型，对应 {@code PractitionerRoleQueryService#listHealthcareServiceIdsByPractitionerAndOrg}。
 *
 * <p>字段契约见 SDD：DM-ADM-006-PractitionerRole-岗位角色-SDD §4.6.3。</p>
 */
@Getter
@Setter
public class PractitionerHealthcareServiceQueryModel {
    /** 岗位角色标识（adm_pr_assign.id）。 */
    private Long assignmentId;

    /** 科室标识（adm_pr_assign.org_id）。 */
    private Long orgId;

    /** 医疗服务标识（adm_pr_assign_service.healthcare_service_id）。 */
    private Long healthcareServiceId;
}
