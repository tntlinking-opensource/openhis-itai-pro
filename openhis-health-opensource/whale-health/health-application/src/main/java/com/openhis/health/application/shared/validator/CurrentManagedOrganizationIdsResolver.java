/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.validator;

import com.openhis.whale.core.application.ILoginDefaultOrganizationResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@link ICurrentUser} 解析当前操作者<strong>管辖科室</strong>（{@code adm_organization.id}，来自 {@code adm_pr_assign} 之 org_id）。
 *
 * <p>库房（药库/药房）与病区均落在 {@code adm_location}，分别经
 * {@link CurrentManagedLocationIdsResolver}、{@link CurrentManagedWardLocationIdsResolver} 解析，勿与本类混用。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentManagedOrganizationIdsResolver {

    /** 当前登录用户与会话上下文（含岗位绑定科室 id 列表）。 */
    private final ICurrentUser currentUser;

    /** 默认登录组织解析器（人员归属机构/会话医院回落；可选注入）。 */
    @Autowired(required = false)
    private ILoginDefaultOrganizationResolver loginDefaultOrganizationResolver;

    /**
     * 是否启用岗位派生科室管辖严格模式（宿主租户跳过）。
     *
     * @return true 表示医院租户且岗位数据范围生效
     */
    public boolean isOrganizationScopeEnforcementActive() {
        if (TenantUtils.isHost()) {
            return false;
        }
        return currentUser.isPractitionerDataScopeActive();
    }

    /**
     * 返回管辖科室主键；严格模式下 empty 表示无管辖科室。
     *
     * @return 去重后的管辖科室 {@code adm_organization.id} 列表
     */
    public List<Long> listManagedOrganizationUnitIdsOrEmpty() {
        if (!currentUser.isAuthenticated()) {
            return List.of();
        }
        List<Long> raw = currentUser.getManagedOrganizationUnitIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> orderedDistinct = new LinkedHashSet<>();
        for (Long orgId : raw) {
            if (orgId != null) {
                orderedDistinct.add(orgId);
            }
        }
        return new ArrayList<>(orderedDistinct);
    }

    /**
     * 断言所选科室落在管辖范围内。
     *
     * @param organizationUnitId   待校验科室 {@code adm_organization.id}
     * @param outOfScopeErrorCode  越权业务错误码
     * @param outOfScopeMessage    越权用户提示
     * @throws BusinessException 越权或科室主键为空时
     */
    public void assertOrganizationUnitInManagedScope(
        Long organizationUnitId,
        String outOfScopeErrorCode,
        String outOfScopeMessage) {
        if (!this.isOrganizationScopeEnforcementActive()) {
            return;
        }
        List<Long> managed = this.listManagedOrganizationUnitIdsOrEmpty();
        if (managed.isEmpty()) {
            // 未绑定岗位管辖：会话科室或默认登录组织（人员归属机构/会话医院）视为有效范围。
            if (this.isOrganizationAllowedWhenNoManagedScope(organizationUnitId)) {
                return;
            }
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
        if (organizationUnitId == null) {
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
        if (managed.contains(organizationUnitId)) {
            return;
        }
        if (isDefaultLoginOrganization(organizationUnitId)) {
            return;
        }
        throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
    }

    /**
     * 读路径：返回会话科室（须在管辖内）；无岗位科室配置时回落会话科室或默认登录组织，避免无 assign 用户被误筛空。
     *
     * @return 有效查询用科室主键；越权或无法回落时 {@code null}
     */
    public Long resolveOrganizationUnitIdForQuery() {
        if (!this.isOrganizationScopeEnforcementActive()) {
            return currentUser.getCurrentOrganizationUnitId();
        }
        List<Long> managed = this.listManagedOrganizationUnitIdsOrEmpty();
        if (managed.isEmpty()) {
            // 未绑定岗位管辖：优先会话科室，否则回落默认登录组织，与 getAvailableOrganizationUnits 口径一致。
            return this.resolveOrganizationUnitIdWhenNoManagedScope(currentUser.getCurrentOrganizationUnitId());
        }
        Long sessionOrganizationUnitId = currentUser.getCurrentOrganizationUnitId();
        if (sessionOrganizationUnitId == null) {
            return null;
        }
        if (managed.contains(sessionOrganizationUnitId)) {
            return sessionOrganizationUnitId;
        }
        if (isDefaultLoginOrganization(sessionOrganizationUnitId)) {
            return sessionOrganizationUnitId;
        }
        return null;
    }

    /**
     * 判定机构是否为当前用户默认登录组织。
     *
     * @param organizationUnitId 机构 id
     * @return true 表示默认登录组织
     */
    private boolean isDefaultLoginOrganization(Long organizationUnitId) {
        if (organizationUnitId == null) {
            return false;
        }
        Long defaultLoginOrgId = this.resolveDefaultLoginOrganizationIdOrNull();
        if (defaultLoginOrgId == null) {
            return false;
        }
        return defaultLoginOrgId.equals(organizationUnitId);
    }

    /**
     * 无岗位管辖科室时，判定机构 id 是否落在有效操作范围（当前会话科室或默认登录组织）。
     *
     * @param organizationUnitId 待判定机构主键
     * @return {@code true} 表示允许继续业务操作
     */
    private boolean isOrganizationAllowedWhenNoManagedScope(Long organizationUnitId) {
        if (organizationUnitId == null) {
            return false;
        }
        Long sessionOrganizationUnitId = currentUser.getCurrentOrganizationUnitId();
        if (sessionOrganizationUnitId != null && sessionOrganizationUnitId.equals(organizationUnitId)) {
            return true;
        }
        return this.isDefaultLoginOrganization(organizationUnitId);
    }

    /**
     * 无岗位管辖科室时解析有效机构：优先入参会话科室，否则回落默认登录组织。
     *
     * @param sessionOrganizationUnitId 当前会话或请求候选科室
     * @return 有效 {@code adm_organization.id}；二者皆无时 {@code null}
     */
    private Long resolveOrganizationUnitIdWhenNoManagedScope(Long sessionOrganizationUnitId) {
        if (sessionOrganizationUnitId != null) {
            return sessionOrganizationUnitId;
        }
        return this.resolveDefaultLoginOrganizationIdOrNull();
    }

    /**
     * 解析默认登录组织（{@code adm_practitioner.org_id}；未填则会话医院）。
     *
     * @return 默认登录组织主键；无法解析时 {@code null}
     */
    private Long resolveDefaultLoginOrganizationIdOrNull() {
        if (loginDefaultOrganizationResolver == null) {
            return null;
        }
        Long userId = currentUser.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return loginDefaultOrganizationResolver.resolveDefaultLoginOrganizationId(
                userId, currentUser.getSessionHospitalId())
            .orElse(null);
    }

    /**
     * 管辖科室与当前会话科室求有效机构：严格模式下会话科室须在管辖内；无岗位管辖时回落默认登录组织。
     *
     * @param sessionOrganizationUnitId 会话或请求候选科室
     * @param outOfScopeErrorCode       越权业务错误码
     * @param outOfScopeMessage         越权用户提示
     * @return 有效的科室主键
     * @throws BusinessException 科室缺失或越权时
     */
    public Long resolveEffectiveOrganizationUnitId(
        Long sessionOrganizationUnitId,
        String outOfScopeErrorCode,
        String outOfScopeMessage) {
        if (!this.isOrganizationScopeEnforcementActive()) {
            return sessionOrganizationUnitId;
        }
        List<Long> managed = this.listManagedOrganizationUnitIdsOrEmpty();
        if (managed.isEmpty()) {
            // 未绑定岗位管辖：写路径与读路径同口径，优先会话科室，否则回落人员归属机构或会话医院。
            Long effectiveOrgId = this.resolveOrganizationUnitIdWhenNoManagedScope(sessionOrganizationUnitId);
            if (effectiveOrgId != null) {
                return effectiveOrgId;
            }
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
        if (sessionOrganizationUnitId == null) {
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
        if (managed.contains(sessionOrganizationUnitId)) {
            return sessionOrganizationUnitId;
        }
        if (isDefaultLoginOrganization(sessionOrganizationUnitId)) {
            return sessionOrganizationUnitId;
        }
        throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
    }

    /**
     * 返回管辖科室主键集合（去重）。
     *
     * @return 管辖科室 id 集合；无管辖时 empty
     */
    public Set<Long> managedOrganizationUnitIdSetOrEmpty() {
        return new LinkedHashSet<>(this.listManagedOrganizationUnitIdsOrEmpty());
    }

    /**
     * 写路径：要求当前会话科室（{@code adm_organization.id}）有效且在岗位管辖内。
     *
     * @param missingErrorCode    会话无科室或越权时的错误码
     * @param missingErrorMessage 会话无科室或越权时的提示
     * @return 有效的会话科室主键
     * @throws BusinessException 未选科室、无法回落默认登录组织或越权时
     */
    public Long requireSessionOrganizationUnitId(String missingErrorCode, String missingErrorMessage) {
        return resolveEffectiveOrganizationUnitId(
            currentUser.getCurrentOrganizationUnitId(),
            missingErrorCode,
            missingErrorMessage);
    }

    /**
     * 写路径：解析会话科室；入参非 {@code null} 时优先使用入参（仍须通过管辖校验）。
     *
     * @param requestedOrganizationUnitId 请求显式科室；{@code null} 时取会话科室
     * @param missingErrorCode            无效时的错误码
     * @param missingErrorMessage         无效时的提示
     * @return 有效的科室主键
     * @throws BusinessException 科室缺失或越权时
     */
    public Long requireOrganizationUnitId(
        Long requestedOrganizationUnitId,
        String missingErrorCode,
        String missingErrorMessage) {
        Long sessionOrganizationUnitId = currentUser.getCurrentOrganizationUnitId();
        Long candidate = requestedOrganizationUnitId;
        if (candidate == null) {
            candidate = sessionOrganizationUnitId;
        }
        return resolveEffectiveOrganizationUnitId(candidate, missingErrorCode, missingErrorMessage);
    }
}
