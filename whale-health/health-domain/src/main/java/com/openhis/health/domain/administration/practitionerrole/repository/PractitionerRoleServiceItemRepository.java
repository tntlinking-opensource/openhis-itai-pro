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

package com.openhis.health.domain.administration.practitionerrole.repository;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 岗位服务项仓储，仅承载 JPA 派生查询。
 *
 * <p>对应 DM-ADM-006 子表 {@code adm_pr_assign_service} 的持久化访问。</p>
 */
public interface PractitionerRoleServiceItemRepository extends JpaRepository<PractitionerRoleServiceItem, Long> {
    /**
     * 按岗位角色标识批量查询服务项。
     *
     * @param assignmentIds 岗位角色标识集合
     * @return 服务项列表
     */
    List<PractitionerRoleServiceItem> findAllByAssignmentIdIn(List<Long> assignmentIds);

    /**
     * 按岗位角色标识批量删除服务项（用于全量替换对齐）。
     *
     * @param assignmentIds 岗位角色标识集合
     */
    void deleteAllByAssignmentIdIn(List<Long> assignmentIds);
}

