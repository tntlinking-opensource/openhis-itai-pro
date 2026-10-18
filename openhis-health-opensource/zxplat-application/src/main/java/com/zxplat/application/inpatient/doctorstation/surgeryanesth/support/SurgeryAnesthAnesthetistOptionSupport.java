/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.support;

import com.zxplat.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.identity.application.RolePlatformConstants;
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
 * 麻醉医师下拉：配置科室 bus_no → 激活 {@code ROLE_DOCTOR} 出诊岗医务人员。
 *
 * <p>与用户管理按出诊科室筛账号同口径（{@code adm_pr_assign.org_id} + 医师岗），返回医务人员主键而非用户主键。
 * 排除系统管理员岗（{@code ROLE_ADMIN}）及平台内置 admin 姓名，避免业务下拉出现运维账号。</p>
 */
@Component
@RequiredArgsConstructor
public class SurgeryAnesthAnesthetistOptionSupport {

    private final SurgeryAnesthAnesthetistOrgProperties properties;

    private final OrganizationQueryService organizationQueryService;

    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /**
     * 加载麻醉医师下拉。
     *
     * @return 选项；未配置科室或无命中时为空列表
     */
    public List<OptionDto> loadAnesthetistOptions() {
        List<String> busNos = this.properties.getAnesthesiaOrgBusNos();
        // 未配置麻醉科 bus_no：不臆造全院医生池
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> trimmedBusNos = new ArrayList<>();
        for (String busNo : busNos) {
            // 空配置项跳过
            if (StringUtils.isEmpty(busNo)) {
                continue;
            }
            trimmedBusNos.add(busNo.trim());
        }
        // 全是空白项
        if (trimmedBusNos.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：bus_no → 科室主键（禁按名称、禁 N+1）
        Map<String, Long> orgIdByBusNo = this.organizationQueryService.mapIdsByBusNos(trimmedBusNos);
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (String busNo : trimmedBusNos) {
            Long orgId = orgIdByBusNo.get(busNo);
            // 库中无对应科室时跳过（配置笔误兜底）
            if (orgId != null) {
                orgIds.add(orgId);
            }
        }
        if (orgIds.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：系统管理员岗人员（与发药调配药师下拉同口径，业务下拉剔除）
        Set<Long> adminPractitionerIds = this.practitionerRoleScopeLoader
            .resolvePractitionerIdsForActivePlatformRole(PlatformRoleCodeConstants.ROLE_ADMIN, null);
        // Pre：各科激活医师岗人员去重（保持 bus_no 配置顺序）
        LinkedHashMap<Long, Practitioner> byId = new LinkedHashMap<>();
        for (Long orgId : orgIds) {
            List<Practitioner> doctors = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
                PlatformRoleCodeConstants.ROLE_DOCTOR, orgId);
            for (Practitioner practitioner : doctors) {
                // 空实体或无主键跳过
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                // 系统管理员岗不入麻醉医师池
                if (adminPractitionerIds.contains(practitioner.getId())) {
                    continue;
                }
                // 平台内置 admin 姓名不入业务下拉（无 ROLE_ADMIN 挂岗时的兜底）
                if (this.isPlatformAdminDisplayName(practitioner.getName())) {
                    continue;
                }
                // 已收录则跳过，避免多科挂岗重复
                if (!byId.containsKey(practitioner.getId())) {
                    byId.put(practitioner.getId(), practitioner);
                }
            }
        }
        return this.toOptions(byId);
    }

    /**
     * 是否平台内置运维展示名（忽略大小写、首尾空白）。
     *
     * @param name 医务人员姓名
     * @return 是 admin 展示名时 true
     */
    private boolean isPlatformAdminDisplayName(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return RolePlatformConstants.ADMIN_USERNAME.equalsIgnoreCase(name.trim());
    }

    /**
     * 医务人员 → 下拉选项。
     *
     * @param byId 有序人员 map
     * @return 选项列表
     */
    private List<OptionDto> toOptions(Map<Long, Practitioner> byId) {
        List<OptionDto> options = new ArrayList<>();
        Set<Map.Entry<Long, Practitioner>> entries = byId.entrySet();
        for (Map.Entry<Long, Practitioner> entry : entries) {
            Practitioner practitioner = entry.getValue();
            options.add(new OptionDto(
                practitioner.getId(),
                String.valueOf(practitioner.getId()),
                practitioner.getName()));
        }
        return options;
    }
}
