/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.repository;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 岗位排班项仓储，仅承载 JPA 派生查询。
 *
 * <p>对应 DM-ADM-006 子表 {@code adm_pr_assign_schedule} 的持久化访问。</p>
 */
public interface PractitionerRoleScheduleItemRepository extends JpaRepository<PractitionerRoleScheduleItem, Long> {
    /**
     * 按岗位角色标识批量查询排班项。
     *
     * @param assignmentIds 岗位角色标识集合
     * @return 排班项列表
     */
    List<PractitionerRoleScheduleItem> findAllByAssignmentIdIn(List<Long> assignmentIds);

    /**
     * 按岗位角色标识批量删除排班项（用于全量替换对齐）。
     *
     * @param assignmentIds 岗位角色标识集合
     */
    void deleteAllByAssignmentIdIn(List<Long> assignmentIds);
}
