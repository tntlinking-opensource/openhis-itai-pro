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

package com.openhis.health.application.common.validator;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@link ICurrentUser} 解析当前操作者<strong>管辖库位</strong>（非登录科室下全部库位），供发药队列、库存明细等读路径收窄。
 *
 * <p>对齐《代码生成通用规范》<strong>B.6.1</strong>：禁止在各 AppService 复制 {@code listPharmaciesByOrganization} /
 * 机构求交等私有解析。</p>
 *
 * <p>过渡口径：{@link ICurrentUser#getManagedLocationIds()} 尚未注入时返回 empty，调用方<strong>不拼</strong>
 * {@code location_id} 谓词；待身份模型落地后由会话填充管辖集合再启用权限收窄。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentManagedLocationIdsResolver {

    /** 当前 HTTP 安全上下文中的登录用户。 */
    private final ICurrentUser currentUser;

    /**
     * 返回当前用户管辖库位主键；未登录或未配置时为 empty（表示不按库位收窄）。
     *
     * @return 稳定去重后的库位 id 列表，永不为 {@code null}
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
     * 返回当前用户管辖库位主键集合视图；empty 表示过渡态「不按库位收窄」。
     *
     * @return 去重后的 id 集合，永不为 {@code null}
     */
    public Set<Long> managedLocationIdSetOrEmpty() {
        return new LinkedHashSet<>(this.listManagedLocationIdsOrEmpty());
    }

    /**
     * 按管辖库位收窄单类启用位置列表（初始化下拉与 Gate 同源）。
     *
     * <ul>
     *   <li>管辖 empty → 返回原列表（过渡：不收窄）</li>
     *   <li>管辖非 empty → 求交；交 empty 时仍返回原列表（避免仅绑药房时药库下拉被误空）</li>
     * </ul>
     *
     * @param locations 启用态位置主数据列表（单形态：药库或药房），可 {@code null}
     * @return 非 null 列表；{@code locations} 为 null 时返回空表
     */
    public List<Location> narrowActiveLocationsByManaged(List<Location> locations) {
        if (locations == null) {
            return Collections.emptyList();
        }
        Set<Long> managed = this.managedLocationIdSetOrEmpty();
        if (managed.isEmpty()) {
            return locations;
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
        return locations;
    }

    /**
     * 从位置实体列表提取主键集合（保持首次出现顺序）。
     *
     * @param locations 位置列表，可 {@code null}
     * @return 非 null 集合，可能为空
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
     * 构造 Gate 白名单：启用位置主数据经 {@link #narrowActiveLocationsByManaged(List)} 收窄后的 id 集合。
     *
     * @param activeLocations 启用态药库/药房全量列表
     * @return 允许作为源/目的/筛选项的位置 id 集合
     */
    public Set<Long> gatedLocationIdsFromActive(List<Location> activeLocations) {
        return this.collectLocationIds(this.narrowActiveLocationsByManaged(activeLocations));
    }

    /**
     * 管辖非空时断言位置落在 Gate 白名单内；管辖 empty 时跳过（过渡）。
     *
     * @param locationId        待校验位置主键
     * @param gatedAllowedIds   Gate 白名单（与下拉同源）
     * @param outOfScopeErrorCode 越权业务码
     * @param outOfScopeMessage   越权提示
     * @throws BusinessException 管辖非空且位置不在白名单
     */
    public void assertLocationInManagedGate(
        Long locationId,
        Set<Long> gatedAllowedIds,
        String outOfScopeErrorCode,
        String outOfScopeMessage
    ) {
        if (this.managedLocationIdSetOrEmpty().isEmpty()) {
            return;
        }
        if (locationId == null || gatedAllowedIds == null || !gatedAllowedIds.contains(locationId)) {
            throw new BusinessException(outOfScopeErrorCode, outOfScopeMessage);
        }
    }

    /**
     * 在「用户管辖库位」与「请求显式库位多选」之间求最终 {@code IN} 集合。
     *
     * <ul>
     *   <li>管辖 empty 且请求 empty → empty（不拼 location 谓词）</li>
     *   <li>管辖 empty 且请求非 empty → 请求去重结果（过渡：尚未启用权限收窄）</li>
     *   <li>管辖非 empty 且请求 empty → 管辖全集</li>
     *   <li>管辖非 empty 且请求非 empty → 求交；交 empty 则 {@link BusinessException}</li>
     * </ul>
     *
     * @param requestedLocationIds  前端/请求体显式选择的库位，可 {@code null}
     * @param outOfScopeErrorCode   所选库位不在管辖范围时的业务码
     * @param outOfScopeMessage     所选库位不在管辖范围时的提示
     * @return 有效库位 id 列表；empty 表示调用方不拼 location 谓词
     * @throws BusinessException 管辖非空但与请求无交集时
     */
    public List<Long> resolveEffectiveLocationScope(
        List<Long> requestedLocationIds,
        String outOfScopeErrorCode,
        String outOfScopeMessage
    ) {
        List<Long> managed = this.listManagedLocationIdsOrEmpty();
        List<Long> requestedDistinct = this.dedupeLocationIds(requestedLocationIds);
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
     * 对库位主键去重并保持首次出现顺序。
     *
     * @param locationIds 原始 id 列表，可 {@code null}
     * @return 去重列表，永不为 {@code null}
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
