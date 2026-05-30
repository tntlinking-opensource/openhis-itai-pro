/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

