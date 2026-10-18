/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.support;

import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.practitionerrole.command.AssignPractitionerRoleCommand;
import com.zxplat.domain.administration.practitionerrole.command.UpdatePractitionerRoleServicesCommand;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
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

    /**
     * 校验分配命令中的医务人员与就诊类型引用均存在。
     *
     * <p>{@code role_id} 可为内置或自定义角色：岗位行挂在「用户勾选且声明了对应 StaffScope 的角色」上，
     * 不再限制仅内置业务角色。</p>
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

    /**
     * 写 Gate：医务人员 id 须全部存在。
     *
     * @param practitionerIds 待校验 id
     * @throws BusinessException {@link ErrorCodeConstants#PRACTITIONER_ROLE_RULE_CONFLICT}
     */
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

    /**
     * 写 Gate：就诊类型（healthcare_service）id 须全部存在。
     *
     * @param serviceIds 待校验 id
     * @throws BusinessException {@link ErrorCodeConstants#PRACTITIONER_ROLE_RULE_CONFLICT}
     */
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
