/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.identity;

import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.framework.core.application.IUserIdsByVisitingOrganizationQuery;
import com.zxplat.framework.identity.domain.Role;
import com.zxplat.framework.identity.infrastructure.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 按出诊科室解析平台用户：医师岗 {@code adm_pr_assign.org_id} → 医务人员 → {@code user_id}。
 *
 * <p>落在 {@code infrastructure.identity}（非 platform）：须读 {@code RoleRepository} 解析
 * {@code ROLE_DOCTOR}，避免 {@code UserAppService} ↔ {@code PlatformRoleResolver} 循环依赖；
 * platform 包 ArchUnit 禁止直连 identity Repository。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthUserIdsByVisitingOrganizationQuery implements IUserIdsByVisitingOrganizationQuery {

    private final RoleRepository roleRepository;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 返回在该科室挂了激活医师出诊岗、且已绑定系统用户的账号主键。
     *
     * @param organizationId HIS 科室主键
     * @return 用户主键列表；入参空、无医师角色或无命中时为空列表
     */
    @Override
    public List<Long> findUserIdsByVisitingOrganizationId(Long organizationId) {
        if (organizationId == null) {
            // 未选科室：调用方不应走本 Port；空列表表示无命中而非「不筛选」
            return Collections.emptyList();
        }
        Long doctorRoleId = this.resolveDoctorRoleId();
        if (doctorRoleId == null) {
            // 租户未播种医师角色：无法识别出诊岗，按无医生处理
            return Collections.emptyList();
        }
        // 加载候选：经岗位 QS 按 ROLE_DOCTOR + org_id 收窄（B.6.4，本类为基础设施编排）
        Set<Long> practitionerIds =
            practitionerRoleQueryService.listPractitionerIdsWithActiveRoleId(doctorRoleId, organizationId);
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            // 该科室没有激活医师出诊岗
            return Collections.emptyList();
        }
        // 医务人员主键 → 已绑定的平台用户主键（未绑账号者不进入用户管理列表）
        return practitionerQueryService.listBoundUserIdsByPractitionerIds(practitionerIds);
    }

    /**
     * 解析内置医师角色主键（{@code zxplat_roles.code = ROLE_DOCTOR}）。
     *
     * @return 角色主键；未播种时 {@code null}
     */
    private Long resolveDoctorRoleId() {
        Optional<Role> role = roleRepository.findByCode(PlatformRoleCodeConstants.ROLE_DOCTOR);
        if (role.isEmpty()) {
            // 未找到 ROLE_DOCTOR：不能把其它岗位误当成出诊医生
            return null;
        }
        return role.get().getId();
    }
}
