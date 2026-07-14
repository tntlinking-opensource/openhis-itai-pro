/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.identity;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.ILoginOrganizationScopeQuery;
import com.openhis.whale.core.application.dto.LoginOrganizationOption;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 登录选医院/科室：基于 {@code adm_organization} 树解析医院归属。
 */
@Service
@RequiredArgsConstructor
public class LoginOrganizationScopeQueryService implements ILoginOrganizationScopeQuery {

    private final OrganizationQueryService organizationQueryService;

    /**
     * 列出启用状态的医院级组织，供登录页选院区。
     *
     * @return 医院选项列表（id、编码、名称）
     */
    @Override
    public List<LoginOrganizationOption> listActiveHospitalOrganizations() {
        List<LoginOrganizationOption> options = new ArrayList<>();
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            // 仅 ACTIVE 且 type=HOSPITAL 的节点进入登录下拉
            if (!isActiveHospital(organization)) {
                continue;
            }
            options.add(toOption(organization));
        }
        return options;
    }

    /**
     * 自任意业务科室 id 向上遍历组织树，解析所属医院 id。
     *
     * @param businessOrganizationId 业务组织主键（科室/病区等）
     * @return 所属医院 id；无法解析或成环时 empty
     */
    @Override
    public Optional<Long> resolveHospitalOrganizationId(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        return walkToHospital(organization, new LinkedHashSet<>());
    }

    /**
     * 判断业务组织是否归属指定医院（含自身即为医院的情况）。
     *
     * @param businessOrganizationId 业务组织主键
     * @param hospitalOrganizationId 目标医院主键
     * @return 归属一致且医院为 ACTIVE 时为 true
     */
    @Override
    public boolean belongsToHospital(Long businessOrganizationId, Long hospitalOrganizationId) {
        if (businessOrganizationId == null || hospitalOrganizationId == null) {
            return false;
        }
        // 业务 id 即医院 id 时直接校验医院类型与状态
        if (businessOrganizationId.equals(hospitalOrganizationId)) {
            Organization organization =
                organizationQueryService.loadOrganizationByIdForDisplay(hospitalOrganizationId);
            return isActiveHospital(organization);
        }
        return resolveHospitalOrganizationId(businessOrganizationId)
            .map(hospitalOrganizationId::equals)
            .orElse(false);
    }

    /**
     * 加载单个业务组织的登录选项（编码+名称）；组织不存在时 empty。
     *
     * @param businessOrganizationId 业务组织主键
     * @return 登录下拉选项
     */
    @Override
    public Optional<LoginOrganizationOption> loadOrganizationOption(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        if (organization == null) {
            return Optional.empty();
        }
        return Optional.of(toOption(organization));
    }

    /**
     * 自当前组织沿 parent 链向上查找 HOSPITAL 节点（环检测 visited）。
     *
     * @param organization 起始组织
     * @param visited      已访问 id 集合（mutate，防环）
     * @return 医院 id；未找到则 empty
     */
    private Optional<Long> walkToHospital(Organization organization, Set<Long> visited) {
        if (organization == null || organization.getId() == null) {
            return Optional.empty();
        }
        if (!visited.add(organization.getId())) {
            return Optional.empty();
        }
        OrganizationType type = OrganizationType.resolveRaw(organization.getTypeEnum());
        if (OrganizationType.HOSPITAL == type) {
            return Optional.of(organization.getId());
        }
        if (organization.getParentId() == null) {
            return Optional.empty();
        }
        Organization parent =
            organizationQueryService.loadOrganizationByIdForDisplay(organization.getParentId());
        return walkToHospital(parent, visited);
    }

    /**
     * 组织是否为 ACTIVE 状态的 HOSPITAL 类型节点。
     *
     * @param organization 组织实体
     * @return 可用医院为 true
     */
    private static boolean isActiveHospital(Organization organization) {
        return organization != null
            && OrganizationType.HOSPITAL == OrganizationType.resolveRaw(organization.getTypeEnum())
            && PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum());
    }

    /**
     * 组织实体 → 登录可选院区 Option（id、busNo、name）。
     *
     * @param organization 组织实体
     * @return 前端下拉 Option
     */
    private static LoginOrganizationOption toOption(Organization organization) {
        return new LoginOrganizationOption(
            organization.getId(),
            StringUtils.defaultString(organization.getBusNo()),
            StringUtils.defaultString(organization.getName()));
    }
}
