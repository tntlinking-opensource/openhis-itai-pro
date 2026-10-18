/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.medicaltech;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医技执行科室共看组：登录 TECH 落在配置组内时，查询可见组内全部执行科室。
 *
 * <p>组内成员按 {@code adm_organization.bus_no} 精确匹配（禁止按名称）。写路径仍用登录科。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechSharedExecutingOrgSupport {

    private final MedicalTechSharedExecutingOrgProperties properties;

    private final OrganizationQueryService organizationQueryService;

    /**
     * 解析登录 TECH 的查询可见执行科室集合。
     *
     * <p>未配置共看、登录科不在任一组、或组内无其它启用 TECH → 仅返回登录科本身。</p>
     *
     * @param loginTechOrgId 登录医技科主键
     * @return 非空可见集合（至少含登录科）；登录科空则空集
     */
    public Set<Long> resolveVisibleExecutingOrgIds(Long loginTechOrgId) {
        LinkedHashSet<Long> visible = new LinkedHashSet<>();
        // 无登录科 → 空集，避免 NPE 扩可见轴
        if (loginTechOrgId == null) {
            return visible;
        }
        visible.add(loginTechOrgId);
        List<List<String>> groups = this.properties.getSharedExecutingOrgBusNoGroups();
        // 未配置共看 → 仅登录科（平台默认单科）
        if (groups == null || groups.isEmpty()) {
            return visible;
        }
        Organization loginOrg =
            this.organizationQueryService.loadOrganizationByIdForDisplay(loginTechOrgId);
        // 登录科无 bus_no 无法匹配配置组 → 不扩可见
        if (loginOrg == null || StringUtils.isEmpty(loginOrg.getBusNo())) {
            return visible;
        }
        String loginBusNo = loginOrg.getBusNo().trim();
        List<String> matchedGroupBusNos = this.findGroupContainingBusNo(groups, loginBusNo);
        // 登录科不在任一组 → 保持仅本科
        if (matchedGroupBusNos.isEmpty()) {
            return visible;
        }
        // Pre：组内 bus_no 批量反查主键（禁按名、禁逐条 N+1）
        Map<String, Long> orgIdByBusNo = this.organizationQueryService.mapIdsByBusNos(matchedGroupBusNos);
        for (String groupBusNo : matchedGroupBusNos) {
            // 空配置项跳过，避免 trim NPE
            if (StringUtils.isEmpty(groupBusNo)) {
                continue;
            }
            Long orgId = orgIdByBusNo.get(groupBusNo.trim());
            // 库中无对应 TECH 科时不写入（配置笔误兜底）
            if (orgId != null) {
                visible.add(orgId);
            }
        }
        return visible;
    }

    /**
     * 登录科是否处于共看组（组内解析到 ≥2 个科室时前端才出筛选项）。
     *
     * @param loginTechOrgId 登录医技科
     * @return 共看且可见科 &gt; 1 时 true
     */
    public boolean isSharedExecutingVisibilityActive(Long loginTechOrgId) {
        Set<Long> visible = this.resolveVisibleExecutingOrgIds(loginTechOrgId);
        return visible.size() > 1;
    }

    /**
     * 共看组筛选项：可见执行科室主键 → 名称（保持配置组内顺序）。
     *
     * @param loginTechOrgId 登录医技科
     * @return 有序 Map；非共看时空 Map
     */
    public Map<Long, String> mapSharedExecutingOrgFilterOptions(Long loginTechOrgId) {
        Set<Long> visible = this.resolveVisibleExecutingOrgIds(loginTechOrgId);
        // 非共看（可见 ≤1）前端不出筛
        if (visible.size() <= 1) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameById = this.organizationQueryService.mapNamesByIds(visible);
        LinkedHashMap<Long, String> ordered = new LinkedHashMap<>();
        for (Long orgId : visible) {
            String name = nameById.get(orgId);
            // 有名称用名称（展示映射 CT/DR）；无名称回落主键字面量
            if (StringUtils.isNotEmpty(name)) {
                ordered.put(orgId, name.trim());
            } else if (orgId != null) {
                ordered.put(orgId, String.valueOf(orgId));
            }
        }
        return ordered;
    }

    /**
     * 请求执行科室是否在可见集合内。
     *
     * @param visibleExecutingOrgIds 可见集合
     * @param requestOrgId           ServiceRequest.org_id
     * @return 可见时 true
     */
    public boolean isVisibleExecutingOrg(Set<Long> visibleExecutingOrgIds, Long requestOrgId) {
        // 无可见轴或无请求执行科 → 不可见
        if (visibleExecutingOrgIds == null || visibleExecutingOrgIds.isEmpty() || requestOrgId == null) {
            return false;
        }
        return visibleExecutingOrgIds.contains(requestOrgId);
    }

    /**
     * 申请单拆单键：执行科落在共看组（如国基 CT↔DR）时返回组内规范主键，同组共一张 AR；
     * 否则返回执行科自身（平台默认：不同执行科拆多张申请单）。
     *
     * <p>行上 {@code ServiceRequest.org_id} 仍写真实执行科，仅拆单分组用本键。</p>
     *
     * @param executingOrgId 本行解析出的执行科室
     * @return 拆单分组键；入参空则 null
     */
    public Long resolveApplyFormSplitKey(Long executingOrgId) {
        // 无执行科 → 无拆单键
        if (executingOrgId == null) {
            return null;
        }
        Set<Long> group = this.resolveVisibleExecutingOrgIds(executingOrgId);
        // 非共看：拆单键即执行科自身（不同执行科拆多张 AR）
        if (group.size() <= 1) {
            return executingOrgId;
        }
        // 规范键取组内最小主键，保证 CT/DR 互为同键、同单不串
        Long canonical = null;
        for (Long orgId : group) {
            // 空主键跳过
            if (orgId == null) {
                continue;
            }
            if (canonical == null || orgId.longValue() < canonical.longValue()) {
                canonical = orgId;
            }
        }
        // 组内全空时回落执行科，避免拆单键为空
        if (canonical == null) {
            return executingOrgId;
        }
        return canonical;
    }

    /**
     * 在配置组中找包含登录科 bus_no 的那一组（精确匹配 trim 后 bus_no）。
     *
     * @param groups     配置组（可空）
     * @param loginBusNo 登录科 bus_no（调用方已 trim）
     * @return 组内 bus_no 列表；未命中空列表
     */
    private List<String> findGroupContainingBusNo(List<List<String>> groups, String loginBusNo) {
        for (List<String> group : groups) {
            // 空组跳过
            if (group == null || group.isEmpty()) {
                continue;
            }
            boolean hit = false;
            for (String member : group) {
                // 精确匹配登录科 bus_no（禁止按名称）
                if (member != null && loginBusNo.equals(member.trim())) {
                    hit = true;
                    break;
                }
            }
            // 命中本组 → 拷贝非空成员供批量反查
            if (hit) {
                List<String> copy = new ArrayList<>();
                for (String member : group) {
                    if (member != null && StringUtils.isNotEmpty(member.trim())) {
                        copy.add(member.trim());
                    }
                }
                return copy;
            }
        }
        return Collections.emptyList();
    }
}
