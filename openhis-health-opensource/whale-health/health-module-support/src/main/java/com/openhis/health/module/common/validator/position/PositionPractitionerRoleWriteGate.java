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

package com.openhis.health.module.common.validator.position;

import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.command.AssignPractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.command.UpdatePractitionerRoleServicesCommand;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.identity.application.RoleAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 岗位写侧跨聚合前置校验（B.6.1.3.1）：医务人员与就诊类型存在性由应用层编排。
 */
@Component
@RequiredArgsConstructor
public class PositionPractitionerRoleWriteGate {

    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final RoleAppService roleAppService;

    /**
     * 校验分配命令中的医务人员与就诊类型引用均存在。
     *
     * @param items 分配项
     */
    public void assertAssignItemsValid(List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        Set<Long> serviceIds = new LinkedHashSet<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : items) {
            if (item == null) {
                continue;
            }
            if (item.getRoleId() != null && !roleAppService.isBuiltInRole(item.getRoleId())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                        "岗位分配仅允许使用内置业务角色");
            }
            if (item.getPractitionerId() != null) {
                practitionerIds.add(item.getPractitionerId());
            }
            if (item.getServiceIds() != null) {
                serviceIds.addAll(item.getServiceIds());
            }
        }
        this.assertPractitionerIdsExist(practitionerIds);
        this.assertHealthcareServiceIdsExist(serviceIds);
    }

    /**
     * 校验服务范围更新命令中的就诊类型均存在。
     *
     * @param cmd 更新服务命令
     */
    public void assertUpdateServicesValid(UpdatePractitionerRoleServicesCommand cmd) {
        if (cmd == null) {
            return;
        }
        Set<Long> serviceIds = new LinkedHashSet<>();
        for (UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand item : cmd.resolveItems()) {
            if (item == null || item.getServiceIds() == null) {
                continue;
            }
            serviceIds.addAll(item.getServiceIds());
        }
        this.assertHealthcareServiceIdsExist(serviceIds);
    }

    private void assertPractitionerIdsExist(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return;
        }
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null || practitionerQueryService.findById(practitionerId) == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "医务人员不存在");
            }
        }
    }

    private void assertHealthcareServiceIdsExist(Collection<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        for (Long serviceId : serviceIds) {
            if (serviceId == null || healthcareServiceQueryService.loadHealthcareServiceById(serviceId) == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "就诊类型不存在");
            }
        }
    }
}
