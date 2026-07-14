/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.validator;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@link ICurrentUser} 解析当前操作者<strong>管辖病区</strong>（{@code adm_location.id}，{@code form_enum=WARD}；
 * {@code NURSE} 岗位在人员维护中绑定 {@code location_id}）。
 *
 * <p>医院租户为硬权限：仅可见/可操作岗位绑定的病区；宿主租户无绑定时可回落机构/全院。</p>
 *
 * <p>药库/药房同属 {@code adm_location} 但由 {@link CurrentManagedLocationIdsResolver} 解析，勿与本类混用。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentManagedWardLocationIdsResolver {

    /** 当前登录用户与会话上下文（含岗位绑定病区 id 列表）。 */
    private final ICurrentUser currentUser;
    /** 位置读侧查询（启用态病区实体批量加载）。 */
    private final LocationQueryService locationQueryService;
    /** 管辖科室解析（无岗位病区时的机构回落口径）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * 是否启用岗位派生数据管辖严格模式（宿主租户跳过）。
     *
     * @return true 表示医院租户且岗位数据范围生效
     */
    public boolean isWardScopeEnforcementActive() {
        if (TenantUtils.isHost()) {
            return false;
        }
        return currentUser.isPractitionerDataScopeActive();
    }

    /**
     * 返回管辖病区主键；严格模式下 empty 表示无管辖病区。
     *
     * @return 去重后的管辖病区 {@code adm_location.id} 列表
     */
    public List<Long> listManagedWardLocationIdsOrEmpty() {
        if (!currentUser.isAuthenticated()) {
            return List.of();
        }
        List<Long> raw = currentUser.getManagedWardLocationIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> orderedDistinct = new LinkedHashSet<>();
        for (Long wardId : raw) {
            if (wardId != null) {
                orderedDistinct.add(wardId);
            }
        }
        return new ArrayList<>(orderedDistinct);
    }

    /**
     * 解析启用态病区实体列表。
     *
     * <p>医院租户为<strong>硬权限</strong>：仅返回岗位绑定的启用态病区；未绑定或绑定均已停用时 empty，
     * 不再回落机构/全院。宿主租户保留无绑定时回落机构/全院口径，便于平台运维。</p>
     *
     * @return 当前操作者可见的启用态病区；永不为 {@code null}
     */
    public List<Location> listManagedActiveWardsOrEmpty() {
        List<Long> wardIds = this.listManagedWardLocationIdsOrEmpty();
        if (TenantUtils.isHost()) {
            // 宿主租户：无岗位绑定时仍回落机构/全院，避免平台侧运维被误筛空。
            if (wardIds.isEmpty()) {
                return this.listFallbackActiveWardsWhenNoManagedScope();
            }
            List<Location> hostWards = locationQueryService.findActiveWardsByIds(wardIds);
            if (hostWards.isEmpty()) {
                return locationQueryService.listAllActiveWards();
            }
            return hostWards;
        }
        // 医院租户硬权限：查不到岗位病区时不展示任何病区，禁止回落全院。
        if (wardIds.isEmpty()) {
            return List.of();
        }
        return locationQueryService.findActiveWardsByIds(wardIds);
    }

    /**
     * 断言病区落在管辖范围内；医院租户未绑定岗位病区时一律拒绝。
     *
     * @param wardLocationId 待校验病区 {@code adm_location.id}
     * @throws BusinessException 越权或病区主键为空时
     */
    public void assertWardInManagedScope(Long wardLocationId) {
        if (TenantUtils.isHost()) {
            return;
        }
        List<Long> managed = this.listManagedWardLocationIdsOrEmpty();
        if (managed.isEmpty()) {
            // 医院租户未绑定管辖病区：写路径禁止操作任意病区。
            throw new BusinessException(
                ErrorCodeConstants.MANAGED_SCOPE_OUT_OF_SCOPE,
                "所选病区不在岗位管辖范围内");
        }
        if (wardLocationId == null || !managed.contains(wardLocationId)) {
            throw new BusinessException(
                ErrorCodeConstants.MANAGED_SCOPE_OUT_OF_SCOPE,
                "所选病区不在岗位管辖范围内");
        }
    }

    /**
     * 返回管辖病区主键集合（去重）。
     *
     * @return 管辖病区 {@code adm_location.id} 集合；无管辖时 empty
     */
    public Set<Long> managedWardLocationIdSetOrEmpty() {
        return new LinkedHashSet<>(this.listManagedWardLocationIdsOrEmpty());
    }

    /**
     * 无岗位病区分配时，按回落机构加载启用态病区；机构未知时取全院病区。
     *
     * @return 候选病区列表，永不为 {@code null}
     */
    private List<Location> listFallbackActiveWardsWhenNoManagedScope() {
        Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        if (organizationUnitId != null) {
            List<Location> orgWards = locationQueryService.listWardsByOrganization(organizationUnitId);
            if (!orgWards.isEmpty()) {
                return orgWards;
            }
        }
        return locationQueryService.listAllActiveWards();
    }

}
