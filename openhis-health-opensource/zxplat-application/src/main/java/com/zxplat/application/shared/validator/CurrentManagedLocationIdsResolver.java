/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.validator;

import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@link ICurrentUser} 解析当前操作者可操作的<strong>库房</strong>（药库 + 药房 + 科室药房）主键集合。
 *
 * <p><strong>数据真源</strong>：库房不是 {@code adm_organization}，而是 {@code adm_location}（{@link Location}）；
 * 药库 {@code form_enum=WAREHOUSE}、药房 {@code form_enum=PHARMACY}、科室药房 {@code form_enum=DEPARTMENT_PHARMACY}。
 * 人员可在「用户/岗位管理」维护
 * {@code warehouseLocationList}、{@code pharmacyLocationList}，落库为 {@code adm_pr_assign.location_id}，
 * 运行时经 {@link ICurrentUser#getManagedLocationIds()} 注入本 Resolver。</p>
 *
 * <p>对齐《代码生成通用规范》<strong>B.6.1</strong>：库存、发药等模块禁止复制库房 id 解析逻辑。</p>
 *
 * <p>与 {@link CurrentManagedOrganizationIdsResolver}（科室 / {@code adm_organization.id}）、
 * {@link CurrentManagedWardLocationIdsResolver}（病区 / {@code adm_location} 之 WARD）分工明确，
 * 三者 id 空间不可混用。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentManagedLocationIdsResolver {

    /** 当前登录用户与会话上下文（含岗位绑定库房 id 列表）。 */
    private final ICurrentUser currentUser;
    /** 位置读侧查询（药库/药房实体批量加载与机构回落）。 */
    private final LocationQueryService locationQueryService;
    /** 管辖科室解析（无岗位库房时的机构回落口径）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * 是否启用岗位派生库房管辖严格模式（宿主租户跳过）。
     *
     * @return true 表示医院租户且岗位数据范围生效
     */
    public boolean isLocationScopeEnforcementActive() {
        if (TenantUtils.isHost()) {
            return false;
        }
        return currentUser.isPractitionerDataScopeActive();
    }

    /**
     * 返回当前用户可操作的药库 + 药房 + 科室药房主键（均为 {@code adm_location.id}）；未登录时为 empty。
     *
     * @return 去重后的管辖库房 id 列表
     */
    public List<Long> listManagedLocationIdsOrEmpty() {
        if (!currentUser.isAuthenticated()) {
            return List.of();
        }
        List<Long> raw = currentUser.getManagedLocationIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> orderedDistinct = new LinkedHashSet<>();
        for (Long locationId : raw) {
            if (locationId != null) {
                orderedDistinct.add(locationId);
            }
        }
        return new ArrayList<>(orderedDistinct);
    }

    /**
     * 返回管辖库房主键集合（去重）。
     *
     * @return 管辖库房 {@code adm_location.id} 集合；无管辖时 empty
     */
    public Set<Long> managedLocationIdSetOrEmpty() {
        return new LinkedHashSet<>(this.listManagedLocationIdsOrEmpty());
    }

    /**
     * 解析启用态药库实体；无岗位分配时回落医院级药库列表。
     *
     * @return 当前操作者可见的启用态药库；永不为 {@code null}
     */
    public List<Location> listManagedActiveWarehousesOrEmpty() {
        List<Long> managedIds = this.listManagedLocationIdsOrEmpty();
        if (managedIds.isEmpty()) {
            return this.listFallbackActiveWarehousesWhenNoManagedScope();
        }
        List<Location> warehouses = locationQueryService.findActiveWarehousesByIds(managedIds);
        if (warehouses.isEmpty() && this.isLocationScopeEnforcementActive()) {
            return List.of();
        }
        if (warehouses.isEmpty()) {
            return locationQueryService.listAllActiveWarehouses();
        }
        return warehouses;
    }

    /**
     * 解析启用态药房与科室药房实体；无岗位分配时回落医院级药房+科室药房并集。
     *
     * @return 当前操作者可见的启用态药房/科室药房；永不为 {@code null}
     */
    public List<Location> listManagedActivePharmaciesOrEmpty() {
        List<Long> managedIds = this.listManagedLocationIdsOrEmpty();
        if (managedIds.isEmpty()) {
            return this.listFallbackActivePharmaciesWhenNoManagedScope();
        }
        List<Location> pharmacies = locationQueryService.findActivePharmaciesByIds(managedIds);
        if (pharmacies.isEmpty() && this.isLocationScopeEnforcementActive()) {
            return List.of();
        }
        if (pharmacies.isEmpty()) {
            return locationQueryService.listAllActivePharmacyPartyLocations();
        }
        return pharmacies;
    }

    /**
     * 按管辖库房 id 收窄单类启用位置列表（初始化下拉与 Gate 同源）。
     *
     * @param locations 候选启用库位列表
     * @return 收窄后的库位列表
     */
    public List<Location> narrowActiveLocationsByManaged(List<Location> locations) {
        if (locations == null) {
            return Collections.emptyList();
        }
        Set<Long> managed = this.managedLocationIdSetOrEmpty();
        if (managed.isEmpty()) {
            if (!this.isLocationScopeEnforcementActive()) {
                return locations;
            }
            // 未绑定岗位库房：按医院级机构范围收窄候选库位，与科室回落口径一致。
            return this.narrowLocationsByFallbackOrganization(locations);
        }
        List<Location> intersected = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            if (managed.contains(location.getId())) {
                intersected.add(location);
            }
        }
        if (!intersected.isEmpty()) {
            return intersected;
        }
        if (this.isLocationScopeEnforcementActive()) {
            return Collections.emptyList();
        }
        return locations;
    }

    /**
     * 从库位实体列表收集主键集合。
     *
     * @param locations 库位实体列表
     * @return 去重后的库位主键集合
     */
    public Set<Long> collectLocationIds(List<Location> locations) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (locations == null) {
            return ids;
        }
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                ids.add(location.getId());
            }
        }
        return ids;
    }

    /**
     * 对启用库位列表做管辖 Gate 后返回主键集合（下拉/查询 IN 谓词同源）。
     *
     * @param activeLocations 启用态库位列表
     * @return Gate 后的库位主键集合
     */
    public Set<Long> gatedLocationIdsFromActive(List<Location> activeLocations) {
        return this.collectLocationIds(this.narrowActiveLocationsByManaged(activeLocations));
    }

    /**
     * 管辖严格模式下断言所选 {@code adm_location.id} 落在 Gate 白名单内。
     *
     * @param locationId          待校验库位主键
     * @param gatedAllowedIds     Gate 白名单库位 id 集合
     * @param outOfScopeErrorCode 越权业务错误码
     * @param outOfScopeMessage   越权用户提示
     * @throws BusinessException 越权或库位主键为空时
     */
    public void assertLocationInManagedGate(
        Long locationId,
        Set<Long> gatedAllowedIds,
        String outOfScopeErrorCode,
        String outOfScopeMessage
    ) {
        if (!this.isLocationScopeEnforcementActive()) {
            return;
        }
        List<Long> managed = this.listManagedLocationIdsOrEmpty();
        if (managed.isEmpty()) {
            // 未绑定岗位库房：仅校验目标库位落在 Gate 白名单（医院级候选）内。
            if (locationId == null || gatedAllowedIds == null || !gatedAllowedIds.contains(locationId)) {
                throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
            }
            return;
        }
        if (locationId == null || gatedAllowedIds == null || !gatedAllowedIds.contains(locationId)) {
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
    }

    /**
     * 在「用户可操作库房」与「请求显式库位多选」之间求最终 {@code IN} 集合。
     *
     * @param requestedLocationIds  请求显式库位多选（可空）
     * @param outOfScopeErrorCode   交集为空时的业务错误码
     * @param outOfScopeMessage     交集为空时的用户提示
     * @return 最终 {@code IN} 谓词库位 id 列表；无约束时 empty
     * @throws BusinessException 严格模式下请求与管辖无交集时
     */
    public List<Long> resolveEffectiveLocationScope(
        List<Long> requestedLocationIds,
        String outOfScopeErrorCode,
        String outOfScopeMessage
    ) {
        List<Long> managed = this.listManagedLocationIdsOrEmpty();
        List<Long> requestedDistinct = this.dedupeLocationIds(requestedLocationIds);
        if (this.isLocationScopeEnforcementActive() && managed.isEmpty()) {
            // 未绑定岗位库房：不按岗位收窄；无显式多选时不拼 location 谓词。
            if (requestedDistinct.isEmpty()) {
                return List.of();
            }
            return requestedDistinct;
        }
        if (requestedDistinct.isEmpty()) {
            if (managed.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(managed);
        }
        if (managed.isEmpty()) {
            return requestedDistinct;
        }
        Set<Long> managedSet = new LinkedHashSet<>(managed);
        List<Long> intersection = new ArrayList<>();
        for (Long locationId : requestedDistinct) {
            if (managedSet.contains(locationId)) {
                intersection.add(locationId);
            }
        }
        if (intersection.isEmpty()) {
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
        return intersection;
    }

    /**
     * 无岗位库房分配时，按回落机构加载启用态药库；机构未知时取全院药库。
     *
     * @return 候选药库列表，永不为 {@code null}
     */
    private List<Location> listFallbackActiveWarehousesWhenNoManagedScope() {
        Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        if (organizationUnitId != null) {
            List<Location> orgWarehouses = locationQueryService.listWarehousesByOrganization(organizationUnitId);
            if (!orgWarehouses.isEmpty()) {
                return orgWarehouses;
            }
        }
        return locationQueryService.listAllActiveWarehouses();
    }

    /**
     * 无岗位库房分配时，按回落机构加载启用态药房与科室药房并集；机构未知时取全院并集。
     *
     * @return 候选药房/科室药房列表，永不为 {@code null}
     */
    private List<Location> listFallbackActivePharmaciesWhenNoManagedScope() {
        Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        if (organizationUnitId != null) {
            List<Location> orgPharmacies = locationQueryService.listPharmacyPartyByOrganization(organizationUnitId);
            if (!orgPharmacies.isEmpty()) {
                return orgPharmacies;
            }
        }
        return locationQueryService.listAllActivePharmacyPartyLocations();
    }

    /**
     * 无岗位库房时，将候选库位收窄到回落机构（医院/会话科室）范围内。
     *
     * @param locations 待收窄库位列表
     * @return 与回落机构匹配的库位；无匹配时原样返回入参
     */
    private List<Location> narrowLocationsByFallbackOrganization(List<Location> locations) {
        Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        if (organizationUnitId == null) {
            return locations;
        }
        List<Location> intersected = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            if (organizationUnitId.equals(location.getOrganizationId())) {
                intersected.add(location);
            }
        }
        if (!intersected.isEmpty()) {
            return intersected;
        }
        return locations;
    }

    /**
     * 库位 id 去重（LinkedHashSet 保序）；跳过 null。
     *
     * @param locationIds 原始列表
     * @return 去重列表
     */
    private List<Long> dedupeLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (Long locationId : locationIds) {
            if (locationId != null) {
                ordered.add(locationId);
            }
        }
        return new ArrayList<>(ordered);
    }
}
