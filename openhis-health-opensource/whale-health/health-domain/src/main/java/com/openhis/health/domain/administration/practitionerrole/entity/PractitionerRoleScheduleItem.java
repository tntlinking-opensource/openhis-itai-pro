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
 * 岗位排班项子实体，对应表 {@code adm_pr_assign_schedule}。
 *
 * <p>排班/可用时间以 JSON 快照形式存储，领域内不在此层解析其结构，
 * 仅保证按岗位（assignmentId）作用域全量对齐写入。</p>
 *
 * <p>对应 DM-ADM-006 §3.3 与 SDD §4.4。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_pr_assign_schedule")
public class PractitionerRoleScheduleItem extends BaseAuditedEntity {
    /** 岗位角色标识，关联主表 {@code adm_pr_assign.id}。 */
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    /** 可用时间 JSON。 */
    @Column(name = "availability_json", nullable = false)
    private String availabilityJson;
}

