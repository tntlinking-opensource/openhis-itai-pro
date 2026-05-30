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

package com.openhis.health.domain.administration.practitionerrole.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位服务项子实体，对应表 {@code adm_pr_assign_service}。
 *
 * <p>表达岗位角色（assignment）可提供的就诊类型集合：
 * 一个岗位角色可关联多个医疗服务（HealthcareService）。</p>
 *
 * <p>对应 DM-ADM-006 §3.2 与 SDD §4.3。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_pr_assign_service")
public class PractitionerRoleServiceItem extends BaseAuditedEntity {
    /** 岗位角色标识，关联主表 {@code adm_pr_assign.id}。 */
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    /** 医疗服务标识，关联 {@code adm_healthcare_service.id}。 */
    @Column(name = "healthcare_service_id", nullable = false)
    private Long healthcareServiceId;
}

