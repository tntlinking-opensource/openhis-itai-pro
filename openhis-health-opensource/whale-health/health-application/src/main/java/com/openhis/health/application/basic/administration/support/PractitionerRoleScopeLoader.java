/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.administration.support;

import com.openhis.health.application.shared.validator.WhaleRoleResolver;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 岗位分配 → 医务人员主键预解析（B.6.1.3 / B.6.1.3.1）。
 *
 * <p>业务查人统一经 {@code adm_pr_assign}：先解析 {@code whale_roles.id}，再查激活岗位分配。
 * 应用层禁止散落 {@code findRoleIdByCode} + 手工 IN 组合，须调用本 Loader。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerRoleScopeLoader {

    private static final int INVENTORY_INIT_PRACTITIONER_PAGE_SIZE = 500;

    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final WhaleRoleResolver whaleRoleResolver;

    /**
     * 按平台角色 code 解析激活岗位上的医务人员主键（查 {@code adm_pr_assign}）。
     *
     * @param platformRoleCode {@code whale_roles.code}，如 {@code ROLE_DOCTOR}
     * @param orgId            科室主键；{@code null} 表示不按 {@code adm_pr_assign.org_id} 收窄
     * @return 医务人员主键集合
     */
    public Set<Long> resolvePractitionerIdsForActivePlatformRole(String platformRoleCode, Long orgId) {
        Long platformRoleId = whaleRoleResolver.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null) {
            return Collections.emptySet();
        }
        return resolvePractitionerIdsForActiveRole(platformRoleId, orgId);
    }

    /**
     * 兼容旧语义码（如 {@code PHARMACIST}）或 {@code ROLE_*} 平台码，再查激活岗位。
     *
     * @param roleFilterCode 岗位过滤语义码
     * @param orgId          科室主键；可空
     * @return 医务人员主键集合
     */
    public Set<Long> resolvePractitionerIdsForActiveRoleFilter(String roleFilterCode, Long orgId) {
        Long platformRoleId = whaleRoleResolver.resolvePlatformRoleId(roleFilterCode);
        if (platformRoleId == null) {
            return Collections.emptySet();
        }
        return resolvePractitionerIdsForActiveRole(platformRoleId, orgId);
    }

    /**
     * 解析拥有指定平台角色且岗位仍激活的医务人员主键集合。
     *
     * @param platformRoleId 平台角色主键（{@code whale_roles.id}）
     * @param orgId          科室主键；{@code null} 表示不按 {@code adm_pr_assign.org_id} 收窄
     * @return 医务人员主键集合；角色为空或无命中时返回空集合
     */
    public Set<Long> resolvePractitionerIdsForActiveRole(Long platformRoleId, Long orgId) {
        if (platformRoleId == null) {
            return Collections.emptySet();
        }
        return practitionerRoleQueryService.listPractitionerIdsWithActiveRoleId(platformRoleId, orgId);
    }

    /**
     * 按平台角色 code 加载激活岗位上的在职医务人员（不分页）。
     *
     * @param platformRoleCode {@code whale_roles.code}
     * @param orgId            科室主键；可空
     * @return 人员列表
     */
    public List<Practitioner> listActivePractitionersForPlatformRole(String platformRoleCode, Long orgId) {
        Set<Long> resolvedIds = resolvePractitionerIdsForActivePlatformRole(platformRoleCode, orgId);
        if (resolvedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return practitionerQueryService.listActivePractitionersByResolvedRoleScope(resolvedIds);
    }

    /**
     * 住院护士站责任护士：在职且于指定病区（{@code adm_pr_assign.location_id}）挂了激活护士岗。
     *
     * @param platformRoleCode 平台角色 code，住院为 {@code ROLE_NURSE}
     * @param wardLocationId   病区位置主键；{@code null} 时返回空列表
     * @return 医务人员列表
     */
    public List<Practitioner> listActivePractitionersForPlatformRoleAtWardLocation(
        String platformRoleCode,
        Long wardLocationId) {
        Long platformRoleId = whaleRoleResolver.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null || wardLocationId == null) {
            return Collections.emptyList();
        }
        Set<Long> resolvedIds =
            practitionerRoleQueryService.listPractitionerIdsWithActiveRoleIdAtLocation(
                platformRoleId, wardLocationId);
        if (resolvedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return practitionerQueryService.listActivePractitionersByResolvedRoleScope(resolvedIds);
    }

    /**
     * 门诊挂号可选医生：在职且于门诊科室（{@code OrganizationClassification.CLINIC}）挂了指定平台医师岗。
     *
     * <p>指定 {@code orgId} 时按该科室收窄；未指定时取全部门诊科室集合批量收窄，排除仅在住院科室挂医师岗的人员。</p>
     *
     * @param platformRoleCode 平台角色 code，门诊挂号为 {@code ROLE_DOCTOR}
     * @param orgId            挂号科室主键；可空
     * @return 医务人员列表
     */
    public List<Practitioner> listActivePractitionersForOutpatientRegistration(String platformRoleCode, Long orgId) {
        Long platformRoleId = whaleRoleResolver.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null) {
            return Collections.emptyList();
        }
        Set<Long> resolvedIds;
        if (orgId != null) {
            // 已选挂号科室：仅在该科室挂了激活医师岗的在职人员。
            resolvedIds = resolvePractitionerIdsForActiveRole(platformRoleId, orgId);
        } else {
            // 未选科室：限定在全部门诊科室范围内有激活医师岗，不把仅负责住院的医师带出。
            List<Organization> outpatientOrganizations =
                organizationQueryService.listOrganizationsForOutpatientRegistration();
            List<Long> outpatientOrganizationIds = new ArrayList<>();
            for (Organization organization : outpatientOrganizations) {
                if (organization != null && organization.getId() != null) {
                    outpatientOrganizationIds.add(organization.getId());
                }
            }
            if (outpatientOrganizationIds.isEmpty()) {
                return Collections.emptyList();
            }
            resolvedIds = practitionerRoleQueryService.listPractitionerIdsWithActiveRoleIdInOrganizations(
                platformRoleId, outpatientOrganizationIds);
        }
        if (resolvedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return practitionerQueryService.listActivePractitionersByResolvedRoleScope(resolvedIds);
    }

    /**
     * 解析拥有指定平台角色、岗位仍激活且挂在给定药房/库位上的医务人员主键集合。
     *
     * @param platformRoleCode 平台角色 code，发药调配为 {@code ROLE_PHARMACIST}
     * @param locationIds      药房 Location 主键集合；空时返回空集合
     * @return 医务人员主键集合
     */
    public Set<Long> resolvePractitionerIdsForActivePlatformRoleInLocations(
        String platformRoleCode,
        Collection<Long> locationIds) {
        Long platformRoleId = whaleRoleResolver.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null) {
            return Collections.emptySet();
        }
        return practitionerRoleQueryService.listPractitionerIdsWithActiveRoleIdInLocations(
            platformRoleId, locationIds);
    }

    /**
     * 分页加载：在职且于给定药房/库位挂了激活平台角色的医务人员。
     *
     * <p>发药调配药师下拉：须同时具备药师岗且 {@code adm_pr_assign.location_id} 落在目标药房集合。</p>
     *
     * @param platformRoleCode 平台角色 code
     * @param locationIds      药房 Location 主键集合；空时返回空分页
     * @param searchKey        关键字
     * @param pageable         分页
     * @param includeInactive  是否含停用主档
     * @return 分页结果
     */
    public Page<Practitioner> pageActivePractitionersForPlatformRoleInLocations(
        String platformRoleCode,
        Collection<Long> locationIds,
        String searchKey,
        Pageable pageable,
        boolean includeInactive) {
        Set<Long> resolvedIds = this.resolvePractitionerIdsForActivePlatformRoleInLocations(
            platformRoleCode, locationIds);
        if (resolvedIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return practitionerQueryService.pageActivePractitionersForPosition(
            resolvedIds, searchKey, pageable, includeInactive);
    }

    /**
     * 分页加载激活岗位上的在职医务人员。
     *
     * @param platformRoleCode 平台角色 code
     * @param orgId            科室主键；可空
     * @param searchKey        关键字
     * @param pageable         分页
     * @param includeInactive  是否含停用主档
     * @return 分页结果
     */
    public Page<Practitioner> pageActivePractitionersForPlatformRole(
        String platformRoleCode,
        Long orgId,
        String searchKey,
        Pageable pageable,
        boolean includeInactive) {
        Set<Long> resolvedIds = resolvePractitionerIdsForActivePlatformRole(platformRoleCode, orgId);
        if (resolvedIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return practitionerQueryService.pageActivePractitionersForPosition(
            resolvedIds, searchKey, pageable, includeInactive);
    }

    /**
     * 库存初始化下拉：在职且于指定机构挂了该平台角色的医务人员。
     *
     * @param platformRoleCode {@code whale_roles.code}
     * @param organizationId   机构主键；{@code null} 时返回空列表
     * @return 人员实体列表
     */
    public List<Practitioner> listActivePractitionersForPlatformRoleInOrganization(
        String platformRoleCode,
        Long organizationId) {
        Long platformRoleId = whaleRoleResolver.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null) {
            return Collections.emptyList();
        }
        return listActivePractitionersForRoleInOrganization(platformRoleId, organizationId);
    }

    /**
     * 库存初始化下拉：在职且于指定机构挂了该平台角色的医务人员。
     *
     * <p>机构口径：{@code adm_pr_assign.org_id = organizationId} 且岗位仍激活。</p>
     *
     * @param platformRoleId 平台角色主键 {@code whale_roles.id}
     * @param organizationId 机构主键；{@code null} 时返回空列表
     * @return 人员实体列表，永不为 null
     */
    public List<Practitioner> listActivePractitionersForRoleInOrganization(
        Long platformRoleId,
        Long organizationId) {
        if (organizationId == null || platformRoleId == null) {
            return Collections.emptyList();
        }
        Set<Long> resolvedPractitionerIds =
            resolvePractitionerIdsForActiveRole(platformRoleId, organizationId);
        if (resolvedPractitionerIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Practitioner> acc = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            Page<Practitioner> page = practitionerQueryService.pageActivePractitionersForPosition(
                resolvedPractitionerIds,
                "",
                PageRequest.of(
                    pageIndex,
                    INVENTORY_INIT_PRACTITIONER_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, FieldNameConstants.ID)));
            if (!page.hasContent()) {
                break;
            }
            for (Practitioner practitioner : page.getContent()) {
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                acc.add(practitioner);
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }
        return acc;
    }

    /**
     * 库存单据经手人下拉：按平台角色装载在职人员。
     *
     * <p>优先认 {@code adm_pr_assign} 且 {@code org_id = organizationId} 的会话科室命中；
     * 会话科室无命中时回退为「该角色全院激活岗位」（{@code org_id} 不收窄），
     * 避免前端仅注入登录人导致经手人下拉只剩一项。</p>
     *
     * @param platformRoleCode {@code whale_roles.code}
     * @param organizationId   会话机构主键；空则返回空列表
     * @return 人员列表，永不为 null
     */
    public List<Practitioner> listActivePractitionersForInventoryDocHandlerRole(
        String platformRoleCode,
        Long organizationId) {
        if (organizationId == null) {
            return Collections.emptyList();
        }
        List<Practitioner> scoped =
            listActivePractitionersForPlatformRoleInOrganization(platformRoleCode, organizationId);
        if (!scoped.isEmpty()) {
            return scoped;
        }
        // 会话科室无岗位命中：回退角色全院激活人员，保证经手人下拉可用
        return listActivePractitionersForPlatformRole(platformRoleCode, null);
    }

    /**
     * 科室库存单据经手人下拉：全体在职医务人员（不按药师/库管岗位收窄）。
     *
     * <p>分页拉取在职主档，供科室盘点等场景下拉 + 拼音检索。</p>
     *
     * @return 人员列表，永不为 null
     */
    public List<Practitioner> listAllActivePractitionersForInventoryDocHandlerSelect() {
        List<Practitioner> acc = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            Page<Practitioner> page = practitionerQueryService.pageActivePractitionersForPosition(
                null,
                "",
                PageRequest.of(
                    pageIndex,
                    INVENTORY_INIT_PRACTITIONER_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, FieldNameConstants.ID)),
                false);
            if (!page.hasContent()) {
                break;
            }
            for (Practitioner practitioner : page.getContent()) {
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                acc.add(practitioner);
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }
        return acc;
    }
}
