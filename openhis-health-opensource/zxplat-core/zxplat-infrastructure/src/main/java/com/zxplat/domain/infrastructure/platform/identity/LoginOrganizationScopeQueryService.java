/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.identity;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.ILoginOrganizationScopeQuery;
import com.zxplat.framework.core.application.dto.LoginOrganizationOption;
import com.zxplat.framework.core.infrastructure.web.RequestScopeCleaner;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 登录选医院/科室：基于 {@code adm_organization} 树解析医院归属。
 *
 * <p>层级为「医院 → 科室」：医院列表仅 {@code HOSPITAL}；科室列表由 Auth 侧组装时排除医院节点。</p>
 *
 * <p>本请求内缓存启用组织树：登录拉科室会对每个科室反复 {@code belongsToHospital}，
 * 避免 N 次 {@code loadOrganizationByIdForDisplay}/父链上翻。</p>
 */
@Service
@RequiredArgsConstructor
public class LoginOrganizationScopeQueryService implements ILoginOrganizationScopeQuery, RequestScopeCleaner {

    private final OrganizationQueryService organizationQueryService;

    /**
     * 本请求启用组织 id → 实体；{@link ThreadLocal} 防止线程池复用串租户树。
     */
    private static final ThreadLocal<Map<Long, Organization>> ORG_BY_ID = new ThreadLocal<>();

    /**
     * 列出启用状态的医院级组织，供登录页选院区。
     *
     * @return 医院选项列表（id、编码、名称）
     */
    @Override
    public List<LoginOrganizationOption> listActiveHospitalOrganizations() {
        List<Organization> hospitals = new ArrayList<>();
        for (Organization organization : this.orgById().values()) {
            // 仅 ACTIVE 且 type=HOSPITAL 的节点进入登录下拉
            if (!isActiveHospital(organization)) {
                continue;
            }
            hospitals.add(organization);
        }
        hospitals.sort(Comparator
            .comparing(Organization::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Organization::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<LoginOrganizationOption> options = new ArrayList<>(hospitals.size());
        for (Organization organization : hospitals) {
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
        Organization organization = this.orgById().get(businessOrganizationId);
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
        Map<Long, Organization> orgById = this.orgById();
        // 业务 id 即医院 id 时直接校验医院类型与状态
        if (businessOrganizationId.equals(hospitalOrganizationId)) {
            return isActiveHospital(orgById.get(hospitalOrganizationId));
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
        Organization organization = this.orgById().get(businessOrganizationId);
        if (organization == null) {
            return Optional.empty();
        }
        return Optional.of(toOption(organization));
    }

    /**
     * 请求结束释放组织树缓存，避免 Tomcat 线程复用串读上一租户。
     */
    @Override
    public void clearRequestScope() {
        ORG_BY_ID.remove();
    }

    /**
     * 本请求懒加载启用组织映射（一次 {@link OrganizationQueryService#getOrganizationTree()}）。
     *
     * @return id → 组织；永不 null
     */
    private Map<Long, Organization> orgById() {
        Map<Long, Organization> cached = ORG_BY_ID.get();
        if (cached != null) {
            return cached;
        }
        Map<Long, Organization> loaded = new HashMap<>();
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization != null && organization.getId() != null) {
                loaded.put(organization.getId(), organization);
            }
        }
        ORG_BY_ID.set(loaded);
        return loaded;
    }

    /**
     * 自当前组织沿 parent 链向上查找 HOSPITAL 节点（环检测 visited；父节点取自本请求缓存）。
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
        Organization parent = this.orgById().get(organization.getParentId());
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
