/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.support;

import com.openhis.health.application.basic.position.dto.StaffOrgLocationItemDto;
import com.openhis.health.application.shared.validator.WhaleRoleResolver;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.identity.application.BuiltInBusinessRoleOptionLoader;
import com.openhis.whale.identity.application.RoleStaffScopeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 工作科室分类门禁：护士角色只能挂护士站（{@code NS}），医技角色只能挂医技科室（{@code TECH}）。
 *
 * <p>与管理病区无关。双角色人员的候选是两类并集，落库时按角色拆开，供登录选科与后续执行归属统计。
 * 自定义角色若声明了 {@link RoleStaffScopeConstants#WORK_ORG}，候选同为护士站∪医技科室。</p>
 */
@Component
@RequiredArgsConstructor
public class WorkDepartmentClassSupport {

    private final WhaleRoleResolver whaleRoleResolver;
    private final OrganizationQueryService organizationQueryService;
    private final BuiltInBusinessRoleOptionLoader builtInBusinessRoleOptionLoader;

    /**
     * 按平台角色解析允许的工作科室分类；护士与医技以外的角色返回 {@code null}。
     *
     * @param roleId 平台角色主键
     * @return {@link OrganizationClassification#NS}、{@link OrganizationClassification#TECH} 或 {@code null}
     */
    public OrganizationClassification resolveClassForRoleId(Long roleId) {
        String roleCode = whaleRoleResolver.findRoleCodeById(roleId);
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        // 护士工作科室 = 护士站，不包含医技科室
        if (WhalePlatformRoleCodeConstants.ROLE_NURSE.equals(normalized)) {
            return OrganizationClassification.NS;
        }
        // 医技（技师）工作科室 = 医技科室，不包含护士站
        if (WhalePlatformRoleCodeConstants.ROLE_MEDICAL_TECH.equals(normalized)) {
            return OrganizationClassification.TECH;
        }
        return null;
    }

    /**
     * 校验本次保存的工作科室都属于已选角色允许的分类。
     *
     * @param rows            工作科室行（{@code orgId}）
     * @param selectedRoleIds 本次勾选的平台角色
     * @throws ValidationException 角色不能配工作科室、科室不存在或分类不匹配时
     */
    public void assertRowsMatchSelectedRoles(
            List<StaffOrgLocationItemDto> rows, List<Long> selectedRoleIds) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<OrganizationClassification> allowed = this.collectAllowedClasses(selectedRoleIds);
        // 没有护士/医技/声明 WORK_ORG 的自定义角色却提交了工作科室：拒绝
        if (allowed.isEmpty()) {
            throw new ValidationException("所选角色不能配置工作科室");
        }
        Map<Long, Organization> organizationById = organizationQueryService.mapByIds(this.collectOrgIds(rows));
        for (StaffOrgLocationItemDto row : rows) {
            if (row == null || row.getOrgId() == null) {
                continue;
            }
            Organization organization = organizationById.get(row.getOrgId());
            // 主键对不上当前租户机构：不能当工作科室
            if (organization == null) {
                throw new ValidationException("工作科室不存在");
            }
            // 分类未维护的脏节点不能作为工作科室
            if (organization.getClassEnum() == null) {
                throw new ValidationException("工作科室分类未维护");
            }
            // 护士只能护士站，医技只能医技科室；双角色或自定义 WORK_ORG 才允许两类并存
            if (!allowed.contains(organization.getClassEnum())) {
                throw new ValidationException("工作科室与角色不匹配：护士只能选护士站，医技只能选医技科室");
            }
        }
    }

    /**
     * 把工作科室行收窄到单个角色允许的分类，供按角色全量替换岗位。
     *
     * @param roleId          写入的平台角色
     * @param rows            表单提交的工作科室全集
     * @param classByOrgId    科室主键 → 机构分类
     * @return 该角色可落库的行；角色无工作科室能力时为空列表
     */
    public List<StaffOrgLocationItemDto> filterRowsForRole(
            Long roleId,
            List<StaffOrgLocationItemDto> rows,
            Map<Long, OrganizationClassification> classByOrgId) {
        List<StaffOrgLocationItemDto> filtered = new ArrayList<>();
        OrganizationClassification singleClass = this.resolveClassForRoleId(roleId);
        Set<OrganizationClassification> allowedForRole = new LinkedHashSet<>();
        if (singleClass != null) {
            allowedForRole.add(singleClass);
        } else if (this.roleClaimsWorkOrg(roleId)) {
            // 自定义角色声明 WORK_ORG：该角色岗位可挂护士站与医技科室
            allowedForRole.add(OrganizationClassification.NS);
            allowedForRole.add(OrganizationClassification.TECH);
        }
        if (allowedForRole.isEmpty() || rows == null || rows.isEmpty()) {
            return filtered;
        }
        for (StaffOrgLocationItemDto row : rows) {
            if (row == null || row.getOrgId() == null) {
                continue;
            }
            OrganizationClassification actual = classByOrgId.get(row.getOrgId());
            // 只把本角色允许分类的科室写到该角色岗位上
            if (actual != null && allowedForRole.contains(actual)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * 批量解析工作科室行的机构分类，供按角色拆分时复用，避免循环内单条查询。
     *
     * @param rows 工作科室行
     * @return 科室主键 → 分类；未命中的主键不出现
     */
    public Map<Long, OrganizationClassification> mapClassByOrgId(List<StaffOrgLocationItemDto> rows) {
        Map<Long, Organization> organizationById = organizationQueryService.mapByIds(this.collectOrgIds(rows));
        Map<Long, OrganizationClassification> classByOrgId = new LinkedHashMap<>();
        for (Map.Entry<Long, Organization> entry : organizationById.entrySet()) {
            Organization organization = entry.getValue();
            if (organization == null || organization.getClassEnum() == null) {
                continue;
            }
            classByOrgId.put(entry.getKey(), organization.getClassEnum());
        }
        return classByOrgId;
    }

    /**
     * 汇总已选角色允许的工作科室分类并集。
     *
     * <p>护士→NS、技师→TECH；仅有声明 {@code WORK_ORG} 的自定义角色时回落为 NS∪TECH。</p>
     *
     * @param selectedRoleIds 已选平台角色
     * @return 允许的分类；都无工作科室能力时为空集
     */
    private Set<OrganizationClassification> collectAllowedClasses(List<Long> selectedRoleIds) {
        Set<OrganizationClassification> allowed = new LinkedHashSet<>();
        if (selectedRoleIds == null) {
            return allowed;
        }
        boolean hasCustomWorkOrg = false;
        for (Long roleId : selectedRoleIds) {
            OrganizationClassification classification = this.resolveClassForRoleId(roleId);
            if (classification != null) {
                allowed.add(classification);
                continue;
            }
            // 自定义等非护士/技师角色：只要声明了 WORK_ORG 即视为可配工作科室
            if (this.roleClaimsWorkOrg(roleId)) {
                hasCustomWorkOrg = true;
            }
        }
        if (allowed.isEmpty() && hasCustomWorkOrg) {
            allowed.add(OrganizationClassification.NS);
            allowed.add(OrganizationClassification.TECH);
        }
        return allowed;
    }

    /**
     * 角色是否声明人员维护块「工作科室」。
     *
     * @param roleId 平台角色主键
     * @return 声明含 {@link RoleStaffScopeConstants#WORK_ORG} 时 true
     */
    private boolean roleClaimsWorkOrg(Long roleId) {
        if (roleId == null) {
            return false;
        }
        List<String> scopes = builtInBusinessRoleOptionLoader.resolveStaffScopesForRoleId(roleId);
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        return scopes.contains(RoleStaffScopeConstants.WORK_ORG);
    }

    /**
     * 从工作科室行收集非空科室主键。
     *
     * @param rows 工作科室行
     * @return 科室主键；无有效行时空列表
     */
    private List<Long> collectOrgIds(List<StaffOrgLocationItemDto> rows) {
        List<Long> orgIds = new ArrayList<>();
        if (rows == null) {
            return orgIds;
        }
        for (StaffOrgLocationItemDto row : rows) {
            if (row != null && row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
        }
        return orgIds;
    }
}
