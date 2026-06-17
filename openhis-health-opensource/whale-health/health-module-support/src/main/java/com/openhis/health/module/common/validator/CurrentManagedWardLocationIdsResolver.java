/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.validator;

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
 * <p>药库/药房同属 {@code adm_location} 但由 {@link CurrentManagedLocationIdsResolver} 解析，勿与本类混用。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentManagedWardLocationIdsResolver {

    private final ICurrentUser currentUser;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * 是否启用岗位派生数据管辖严格模式（宿主租户跳过）。
     */
    public boolean isWardScopeEnforcementActive() {
        if (TenantUtils.isHost()) {
            return false;
        }
        return currentUser.isPractitionerDataScopeActive();
    }

    /**
     * 返回管辖病区主键；严格模式下 empty 表示无管辖病区。
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
     * 解析启用态病区实体列表；无岗位分配时回落医院级病区列表。
     */
    public List<Location> listManagedActiveWardsOrEmpty() {
        List<Long> wardIds = this.listManagedWardLocationIdsOrEmpty();
        if (wardIds.isEmpty()) {
            return this.listFallbackActiveWardsWhenNoManagedScope();
        }
        List<Location> wards = locationQueryService.findActiveWardsByIds(wardIds);
        if (wards.isEmpty() && this.isWardScopeEnforcementActive()) {
            return List.of();
        }
        if (wards.isEmpty()) {
            return locationQueryService.listAllActiveWards();
        }
        return wards;
    }

    /**
     * 断言病区落在管辖范围内。
     */
    public void assertWardInManagedScope(Long wardLocationId) {
        if (!this.isWardScopeEnforcementActive()) {
            return;
        }
        List<Long> managed = this.listManagedWardLocationIdsOrEmpty();
        if (managed.isEmpty()) {
            // 未绑定岗位病区：按医院级操作放行，仅校验病区落在回落机构范围内。
            if (this.isWardAllowedWhenNoManagedScope(wardLocationId)) {
                return;
            }
            if (wardLocationId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.MANAGED_SCOPE_OUT_OF_SCOPE,
                    "所选病区不在岗位管辖范围内");
            }
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

    /**
     * 无岗位病区时，判定病区 id 是否落在回落机构（医院/会话科室）范围内。
     *
     * @param wardLocationId 待判定病区主键
     * @return {@code true} 表示允许继续业务操作
     */
    private boolean isWardAllowedWhenNoManagedScope(Long wardLocationId) {
        if (wardLocationId == null) {
            return false;
        }
        for (Location ward : this.listFallbackActiveWardsWhenNoManagedScope()) {
            if (ward != null && wardLocationId.equals(ward.getId())) {
                return true;
            }
        }
        return false;
    }
}
