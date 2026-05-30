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

package com.openhis.health.domain.administration.organization.service;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.repository.OrganizationRepository;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.OrganizationStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 机构读侧查询服务。
 *
 * <p>职责：按业务场景（门诊挂号、入院、药房、仓储等）筛选启用机构，并提供主键加载、业务单号解析与废弃前置校验。</p>
 *
 * <p>边界：只读；树形展示当前多为平铺启用列表由前端组装；废弃校验仅检查是否存在启用子节点。</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationQueryService {

    private final OrganizationRepository organizationRepository;

    /**
     * 门诊挂号可选机构：启用且分类为门诊（诊所）。
     *
     * @return 机构列表
     */
    public List<Organization> listOrganizationsForOutpatientRegistration() {
        return organizationRepository.findAllByStatusEnumAndClassEnum(
            OrganizationStatus.ACTIVE.getCode(), OrganizationClassification.CLINIC);
    }

    /**
     * 门诊挂号机构树数据入口（当前与 {@link #listOrganizationsForOutpatientRegistration()} 同源）。
     *
     * @return 机构列表
     */
    public List<Organization> loadOrganizationTreeForOutpatientRegistration() {
        return this.listOrganizationsForOutpatientRegistration();
    }

    /**
     * 住院入院可选机构：启用且分类为住院。
     *
     * @return 机构列表
     */
    public List<Organization> listOrganizationsForInpatientAdmission() {
        return organizationRepository.findAllByStatusEnumAndClassEnum(
            OrganizationStatus.ACTIVE.getCode(), OrganizationClassification.INPATIENT);
    }

    /**
     * 按主键加载机构用于展示（不存在则 {@code null}）。
     *
     * @param organizationId 机构主键
     * @return 机构实体或 {@code null}
     */
    public Organization loadOrganizationByIdForDisplay(Long organizationId) {
        return organizationRepository.findById(organizationId).orElse(null);
    }

    /**
     * 批量解析机构名称（列表/发药单等场景避免 N+1，对齐 A.9.1）。
     *
     * @param organizationIds 机构主键集合；{@code null} 或空时返回空映射
     * @return 机构主键 → 名称；未命中或无名称时不写入该键
     */
    public Map<Long, String> mapNamesByIds(Collection<Long> organizationIds) {
        Map<Long, String> nameByOrganizationId = new HashMap<>();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return nameByOrganizationId;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long organizationId : organizationIds) {
            if (organizationId != null) {
                distinctIds.add(organizationId);
            }
        }
        if (distinctIds.isEmpty()) {
            return nameByOrganizationId;
        }
        List<Organization> organizations = organizationRepository.findAllByIdIn(distinctIds);
        for (Organization organization : organizations) {
            if (organization.getId() != null && organization.getName() != null) {
                nameByOrganizationId.put(organization.getId(), organization.getName());
            }
        }
        return nameByOrganizationId;
    }

    /**
     * 判断机构是否允许作废：若仍存在启用子机构则不可作废，防止断树或遗留悬挂数据。
     *
     * @param organizationId 待判断机构主键
     * @return {@code true} 表示当前无启用子节点，可作废
     */
    public boolean checkOrganizationAbolishable(Long organizationId) {
        return !organizationRepository.existsByParentIdAndStatusEnum(
            organizationId, OrganizationStatus.ACTIVE.getCode());
    }

    /**
     * 全部启用机构列表（平铺），供通用树/字典组装。
     *
     * @return 启用机构列表
     */
    public List<Organization> getOrganizationTree() {
        return organizationRepository.findAllByStatusEnum(OrganizationStatus.ACTIVE.getCode());
    }

    /**
     * 药房业务可选启用机构（分类为药房）。
     *
     * @return 机构列表
     */
    public List<Organization> getOrganizationTreeForPharmacy() {
        return organizationRepository.findAllByStatusEnumAndClassEnum(
            OrganizationStatus.ACTIVE.getCode(), OrganizationClassification.PHARMACY);
    }

    /**
     * 仓储业务可选启用机构（分类为仓储）。
     *
     * @return 机构列表
     */
    public List<Organization> getOrganizationTreeForStorage() {
        return organizationRepository.findAllByStatusEnumAndClassEnum(
            OrganizationStatus.ACTIVE.getCode(), OrganizationClassification.STORAGE);
    }

    /**
     * 机构字典树数据（与 {@link #getOrganizationTree()} 同源）。
     *
     * @return 启用机构列表
     */
    public List<Organization> getOrganizationDictTree() {
        return this.getOrganizationTree();
    }

    /**
     * 责任科室等场景：在启用机构中筛出已维护分类枚举的行，排除分类为空的脏数据节点。
     *
     * @return 带非空 {@code classEnum} 的机构子集
     */
    /**
     * 责任科室等场景：在启用机构中筛出已维护分类枚举的行，排除分类为空的脏数据节点。
     *
     * @return 带非空 {@code classEnum} 的机构子集
     */
    public List<Organization> getOrganizationTreeForResponsibilityDept() {
        List<Organization> all = this.getOrganizationTree();
        List<Organization> result = new ArrayList<>();
        // Loop：遍历机构列表，过滤出分类非空的节点
        for (Organization organization : all) {
            if (organization.getClassEnum() != null) {
                result.add(organization);
            }
        }
        return result;
    }

    /**
     * 按主键批量解析机构名称（工作台列表等轻量展示）。
     *
     * @param organizationIds 机构主键集合
     * @return 机构主键 → 名称
     */
    public Map<Long, String> findOrganizationNamesByIds(Collection<Long> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : organizationIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>(idSet.size());
        for (Organization o : organizationRepository.findAllByIdIn(idSet)) {
            out.put(o.getId(), StringUtils.defaultString(o.getName()));
        }
        return out;
    }

    /**
     * 急诊分诊可选科室：启用且 {@link OrganizationType#DEPARTMENT}。
     *
     * <p>上下文为医院/机构时取直接子科室；上下文本身为科室时取同上级下的兄弟科室（含自身）。</p>
     *
     * @param scopeOrganizationId 登录或初始化上下文中的机构/科室主键
     * @return 按 displayOrder、主键升序的科室列表
     */
    public List<Organization> listActiveDepartmentsForTriageScope(Long scopeOrganizationId) {
        if (scopeOrganizationId == null) {
            return List.of();
        }
        Organization scope = loadOrganizationByIdForDisplay(scopeOrganizationId);
        if (scope == null) {
            return List.of();
        }
        Long siblingParentId =
                OrganizationType.DEPARTMENT.getCode().equals(scope.getTypeEnum())
                        ? scope.getParentId()
                        : scopeOrganizationId;
        List<Organization> departments = new ArrayList<>();
        if (siblingParentId != null) {
            for (Organization org : organizationRepository.findAllByParentId(siblingParentId)) {
                if (isActiveDepartment(org)) {
                    departments.add(org);
                }
            }
        }
        if (isActiveDepartment(scope)
                && departments.stream().noneMatch(o -> Objects.equals(o.getId(), scope.getId()))) {
            departments.add(scope);
        }
        departments.sort(
                Comparator.comparing(
                                Organization::getDisplayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Organization::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return departments;
    }

    private static boolean isActiveDepartment(Organization org) {
        return org != null
                && OrganizationType.DEPARTMENT.getCode().equals(org.getTypeEnum())
                && OrganizationStatus.ACTIVE.getCode().equals(org.getStatusEnum());
    }

    /**
     * 按业务单号解析机构（首条匹配）。
     *
     * @param busNo 机构业务单号
     * @return 机构实体；无匹配时 {@code null}
     */
    public Organization findFirstByBusNo(String busNo) {
        return organizationRepository.findFirstByBusNo(busNo).orElse(null);
    }
}
