/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.medicaltech;

import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.enums.OrganizationClassification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 门诊护士站 / 医技站耗材可见性轴：按发放耗材房挂靠科室及其组织祖先解析 TECH 归属。
 *
 * <p>口径见可见性 DD：耗材不跟诊疗绑定。有效库位优先 {@code DeviceRequest.locationId}，
 * 为空时回落主发放行 {@code DeviceDispense.locationId}（院点常见请求未写库位、仅发放落房）。
 * 挂靠科室 class=TECH（或祖先为 TECH）→ 护士站排除；医技站仅当挂靠科室 = 登录科，
 * 或挂靠最近 TECH / 登录科互为祖先链对齐时可见——<strong>禁止</strong>「任意 TECH 互看」
 * （检验科不得见超声科耗材房待发）。</p>
 */
@Component
@RequiredArgsConstructor
public class DeviceSupplyTechVisibilitySupport {

    /** 组织树上溯最大深度，防止脏数据成环。 */
    private static final int MAX_ANCESTOR_DEPTH = 16;

    /** 组织只读：挂靠科室与祖先批量装载。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 自种子组织主键批量装载实体，并沿 {@code parentId} 上溯补齐祖先（分批 IN，禁止按节点 N+1）。
     *
     * @param seedOrganizationIds 挂靠科室等种子主键
     * @return 含祖先的组织索引；空入参返回空 Map
     */
    public Map<Long, Organization> mapOrganizationsWithAncestors(Collection<Long> seedOrganizationIds) {
        Map<Long, Organization> orgById = new HashMap<>();
        if (seedOrganizationIds == null || seedOrganizationIds.isEmpty()) {
            return orgById;
        }
        Set<Long> pending = new LinkedHashSet<>();
        for (Long organizationId : seedOrganizationIds) {
            if (organizationId != null) {
                pending.add(organizationId);
            }
        }
        int depth = 0;
        while (!pending.isEmpty() && depth < MAX_ANCESTOR_DEPTH) {
            Set<Long> missing = new LinkedHashSet<>();
            for (Long organizationId : pending) {
                if (!orgById.containsKey(organizationId)) {
                    missing.add(organizationId);
                }
            }
            if (missing.isEmpty()) {
                break;
            }
            Map<Long, Organization> batch = this.organizationQueryService.mapByIds(missing);
            Set<Long> nextParents = new LinkedHashSet<>();
            for (Long organizationId : missing) {
                Organization organization = batch.get(organizationId);
                if (organization == null) {
                    continue;
                }
                orgById.put(organizationId, organization);
                Long parentId = organization.getParentId();
                // 尚未装载的父节点进入下一轮上溯
                if (parentId != null && !orgById.containsKey(parentId)) {
                    nextParents.add(parentId);
                }
            }
            pending = nextParents;
            depth++;
        }
        return orgById;
    }

    /**
     * 挂靠科室自身或其祖先是否为 TECH（护士站耗材轴：命中则排除）。
     *
     * @param organizationId 挂靠科室主键
     * @param orgById        须已含祖先的组织索引
     * @return 链路含 TECH 时 true；空或解析不到时 false（护士站兜底可见）
     */
    public boolean isTechOwningOrganization(Long organizationId, Map<Long, Organization> orgById) {
        return this.resolveNearestTechOrganizationId(organizationId, orgById) != null;
    }

    /**
     * 解析耗材有效库位：请求库位优先，空则回落主发放库位。
     *
     * @param requestLocationId              请求 {@code location_id}
     * @param deviceRequestId                耗材请求主键
     * @param primaryDispenseLocationByReqId 请求→主发放库位
     * @return 有效库位；均空则 null
     */
    public Long resolveEffectiveDeviceLocationId(
        Long requestLocationId,
        Long deviceRequestId,
        Map<Long, Long> primaryDispenseLocationByReqId) {
        if (requestLocationId != null) {
            return requestLocationId;
        }
        if (deviceRequestId == null || primaryDispenseLocationByReqId == null) {
            return null;
        }
        return primaryDispenseLocationByReqId.get(deviceRequestId);
    }

    /**
     * 自库位挂靠科室判定是否属 TECH 耗材轴（含组织祖先）。
     *
     * @param locationId   库位主键
     * @param locationById 库位索引
     * @param orgById      须已含祖先的组织索引
     * @return TECH 轴时 true
     */
    public boolean isTechDeviceLocation(
        Long locationId,
        Map<Long, Location> locationById,
        Map<Long, Organization> orgById) {
        if (locationId == null || locationById == null || orgById == null) {
            return false;
        }
        Location location = locationById.get(locationId);
        if (location == null || location.getOrganizationId() == null) {
            return false;
        }
        return this.isTechOwningOrganization(location.getOrganizationId(), orgById);
    }

    /**
     * 解析挂靠科室沿祖先链最近的 TECH 组织主键（含自身）。
     *
     * @param organizationId 起点组织
     * @param orgById        须已含祖先的组织索引
     * @return 最近 TECH 主键；无则 null
     */
    public Long resolveNearestTechOrganizationId(Long organizationId, Map<Long, Organization> orgById) {
        if (organizationId == null || orgById == null || orgById.isEmpty()) {
            return null;
        }
        Long cursor = organizationId;
        Set<Long> visited = new HashSet<>();
        int depth = 0;
        while (cursor != null && depth < MAX_ANCESTOR_DEPTH && visited.add(cursor)) {
            Organization organization = orgById.get(cursor);
            if (organization == null) {
                return null;
            }
            // 自身或祖先命中 TECH → 作为耗材归属医技科
            if (organization.getClassEnum() == OrganizationClassification.TECH) {
                return cursor;
            }
            cursor = organization.getParentId();
            depth++;
        }
        return null;
    }

    /**
     * 耗材房挂靠科室是否归属当前登录 TECH 科可见轴。
     *
     * <p>规则（与可见性 DD §3 表一致）：挂靠科室 = 登录科；挂靠最近 TECH = 登录科；
     * 挂靠最近 TECH 为登录科祖先（药房挂「医技」上级）；或登录科为挂靠科室祖先且挂靠侧已落 TECH。
     * <strong>不</strong>因「双方都是 TECH」就互看——检验科不得见超声科待发耗材。</p>
     *
     * @param locationOrganizationId 库位挂靠科室
     * @param loginTechOrgId         登录 TECH 科
     * @param orgById                须已含双方祖先的组织索引
     * @return 本登录科可见时 true
     */
    public boolean belongsToLoginTechOrganization(
        Long locationOrganizationId,
        Long loginTechOrgId,
        Map<Long, Organization> orgById) {
        if (locationOrganizationId == null || loginTechOrgId == null || orgById == null) {
            return false;
        }
        // 挂靠即登录科（检验科登录 → 仅检验科下属耗材房）
        if (loginTechOrgId.equals(locationOrganizationId)) {
            return true;
        }
        Long owningTechOrgId = this.resolveNearestTechOrganizationId(locationOrganizationId, orgById);
        // 挂靠侧解析不到 TECH → 不归任何医技站（护士站兜底）
        if (owningTechOrgId == null) {
            return false;
        }
        // 挂靠最近 TECH 就是登录科（房挂在本科）
        if (loginTechOrgId.equals(owningTechOrgId)) {
            return true;
        }
        // 归属 TECH 是登录科祖先（药房挂医技上级、登录子 TECH 科）
        if (this.isAncestorOf(owningTechOrgId, loginTechOrgId, orgById)) {
            return true;
        }
        // 登录科是挂靠科室祖先，且挂靠侧已落在 TECH 子树
        if (this.isAncestorOf(loginTechOrgId, locationOrganizationId, orgById)) {
            return true;
        }
        // 挂靠侧属其它 TECH、与登录科无祖先链 → 本站不可见
        return false;
    }

    /**
     * 判断 {@code ancestorOrganizationId} 是否为 {@code descendantOrganizationId} 的严格祖先。
     *
     * @param ancestorOrganizationId   候选祖先
     * @param descendantOrganizationId 候选后代
     * @param orgById                  组织索引
     * @return 是严格祖先时 true
     */
    public boolean isAncestorOf(
        Long ancestorOrganizationId,
        Long descendantOrganizationId,
        Map<Long, Organization> orgById) {
        if (ancestorOrganizationId == null
            || descendantOrganizationId == null
            || orgById == null
            || ancestorOrganizationId.equals(descendantOrganizationId)) {
            return false;
        }
        Long cursor = descendantOrganizationId;
        Set<Long> visited = new HashSet<>();
        int depth = 0;
        while (cursor != null && depth < MAX_ANCESTOR_DEPTH && visited.add(cursor)) {
            Organization organization = orgById.get(cursor);
            if (organization == null) {
                return false;
            }
            Long parentId = organization.getParentId();
            if (ancestorOrganizationId.equals(parentId)) {
                return true;
            }
            cursor = parentId;
            depth++;
        }
        return false;
    }
}
