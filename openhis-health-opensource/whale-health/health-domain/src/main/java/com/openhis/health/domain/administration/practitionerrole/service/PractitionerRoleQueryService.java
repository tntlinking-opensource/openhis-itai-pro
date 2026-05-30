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

package com.openhis.health.domain.administration.practitionerrole.service;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleServiceItem;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerHealthcareServiceQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerOrgIdQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleRepository;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleServiceItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 执业角色读侧查询服务，提供医务人员岗位分配的只读查询能力。
 *
 * <p>对应 DM-ADM-006 §6.2 读侧方法集。</p>
 */
@Service
@RequiredArgsConstructor
public class PractitionerRoleQueryService {
    private final PractitionerRoleRepository repository;
    private final PractitionerRoleServiceItemRepository serviceItemRepository;

    /**
     * 查询指定医务人员当前处于激活状态的执业角色列表。
     *
     * @param practitionerId 医务人员主键
     * @return 岗位分配查询模型列表，无则返回空列表
     */
    /**
     * 查询指定医务人员当前处于激活状态的执业角色列表。
     *
     * @param practitionerId 医务人员主键
     * @return 岗位分配查询模型列表，无则返回空列表
     */
    public List<PractitionerRoleAssignmentQueryModel> listAssignmentsByPractitioner(Long practitionerId) {
        List<PractitionerRole> roles = repository.findAllByPractitionerIdAndActiveFlagTrue(practitionerId);
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }

        // Loop：收集岗位分配ID列表，用于批量查询服务项
        List<Long> assignmentIds = new ArrayList<>();
        for (PractitionerRole role : roles) {
            assignmentIds.add(role.getId());
        }

        List<PractitionerRoleServiceItem> serviceItems = serviceItemRepository.findAllByAssignmentIdIn(assignmentIds);
        // Loop：按assignmentId分组聚合服务项ID
        Map<Long, List<Long>> serviceIdsByAssignmentId = new LinkedHashMap<>();
        for (PractitionerRoleServiceItem row : serviceItems) {
            List<Long> list = serviceIdsByAssignmentId.get(row.getAssignmentId());
            if (list == null) {
                list = new ArrayList<>();
                serviceIdsByAssignmentId.put(row.getAssignmentId(), list);
            }
            list.add(row.getHealthcareServiceId());
        }

        // Loop：组装岗位分配查询模型列表
        List<PractitionerRoleAssignmentQueryModel> result = new ArrayList<>();
        for (PractitionerRole role : roles) {
            PractitionerRoleAssignmentQueryModel model = new PractitionerRoleAssignmentQueryModel();
            model.setAssignmentId(role.getId());
            model.setRoleCode(role.getRoleCode());
            model.setOrgId(role.getOrgId());
            model.setLocationId(role.getLocationId());
            model.setServiceIds(serviceIdsByAssignmentId.get(role.getId()));
            result.add(model);
        }
        return result;
    }

    /**
     * 拥有指定激活角色编码的全部医务人员主键（去重，插入顺序稳定）。
     *
     * <p>替代「对每个在职人员调用 {@link #listAssignmentsByPractitioner(Long)}」的 O(N) 次读放大。</p>
     *
     * @param roleCode 角色编码（与左侧岗位菜单映射一致，如护士=2）
     * @return 无匹配时返回空集（不可变）
     */
    /**
     * 拥有指定激活角色编码的全部医务人员主键（去重，插入顺序稳定）。
     *
     * <p>替代「对每个在职人员调用 {@link #listAssignmentsByPractitioner(Long)}」的 O(N) 次读放大。</p>
     *
     * @param roleCode 角色编码（与左侧岗位菜单映射一致，如护士=2）
     * @return 无匹配时返回空集（不可变）
     */
    public Set<Long> listPractitionerIdsWithActiveRole(Integer roleCode) {
        if (roleCode == null) {
            return Collections.emptySet();
        }
        List<PractitionerRole> rows = repository.findAllByRoleCodeAndActiveFlagTrue(roleCode);
        if (rows.isEmpty()) {
            return Collections.emptySet();
        }
        // Loop：遍历岗位列表，提取医务人员主键并去重（LinkedHashSet保持插入顺序）
        Set<Long> ids = new LinkedHashSet<>();
        for (PractitionerRole row : rows) {
            if (row.getPractitionerId() != null) {
                ids.add(row.getPractitionerId());
            }
        }
        return ids;
    }

    /**
     * 查询指定医务人员当前激活岗位所覆盖的科室 ID 去重列表。
     *
     * <p>多个岗位可能属于同一科室，通过 LinkedHashSet 去重并保持插入顺序。</p>
     *
     * @param practitionerId 医务人员主键
     * @return 科室查询模型去重列表（保持顺序），无则返回空列表
     */
    /**
     * 查询指定医务人员当前激活岗位所覆盖的科室ID去重列表。
     *
     * <p>多个岗位可能属于同一科室，通过 LinkedHashSet 去重并保持插入顺序。</p>
     *
     * @param practitionerId 医务人员主键
     * @return 科室查询模型去重列表（保持顺序），无则返回空列表
     */
    public List<PractitionerOrgIdQueryModel> listOrganizationIdsByPractitioner(Long practitionerId) {
        Set<Long> ids = new LinkedHashSet<>();
        // Loop：遍历医务人员激活岗位，提取科室ID并去重
        for (PractitionerRoleAssignmentQueryModel row : this.listAssignmentsByPractitioner(practitionerId)) {
            if (row.getOrgId() != null) {
                ids.add(row.getOrgId());
            }
        }
        // Loop：将去重后的科室ID转换为查询模型列表
        List<PractitionerOrgIdQueryModel> result = new ArrayList<>();
        for (Long orgId : ids) {
            PractitionerOrgIdQueryModel model = new PractitionerOrgIdQueryModel();
            model.setPractitionerId(practitionerId);
            model.setOrgId(orgId);
            result.add(model);
        }
        return result;
    }

    /**
     * 查询指定医务人员在特定科室下激活岗位所绑定的医疗服务 ID 列表。
     *
     * <p>当前为轻量占位实现，待 readmodel 投影器上线后替换为结构化 JSON 解析逻辑。</p>
     *
     * @param practitionerId 医务人员主键
     * @param orgId          科室主键
     * @return 人员+科室下的就诊类型查询模型列表，无则返回空列表
     */
    /**
     * 查询指定医务人员在特定科室下激活岗位所绑定的医疗服务ID列表。
     *
     * <p>当前为轻量占位实现，待 readmodel 投影器上线后替换为结构化 JSON 解析逻辑。</p>
     *
     * @param practitionerId 医务人员主键
     * @param orgId          科室主键
     * @return 人员+科室下的就诊类型查询模型列表，无则返回空列表
     */
    public List<PractitionerHealthcareServiceQueryModel> listHealthcareServiceIdsByPractitionerAndOrg(Long practitionerId, Long orgId) {
        List<PractitionerRole> roles = repository.findAllByPractitionerIdAndOrgIdAndActiveFlagTrue(practitionerId, orgId);
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }

        // Loop：收集岗位分配ID列表
        List<Long> assignmentIds = new ArrayList<>();
        for (PractitionerRole role : roles) {
            assignmentIds.add(role.getId());
        }

        List<PractitionerRoleServiceItem> serviceItems = serviceItemRepository.findAllByAssignmentIdIn(assignmentIds);
        // Loop：组装医疗服务查询模型列表
        List<PractitionerHealthcareServiceQueryModel> result = new ArrayList<>();
        for (PractitionerRoleServiceItem row : serviceItems) {
            PractitionerHealthcareServiceQueryModel model = new PractitionerHealthcareServiceQueryModel();
            model.setAssignmentId(row.getAssignmentId());
            model.setOrgId(orgId);
            model.setHealthcareServiceId(row.getHealthcareServiceId());
            result.add(model);
        }
        return result;
    }

    /**
     * 列出指定医务人员全部岗位分配行（含停用，供岗位管理工作台列表）。
     *
     * @param practitionerId 医务人员主键
     * @return 岗位分配实体列表；无记录时返回空列表
     */
    public List<PractitionerRole> listAllRolesByPractitioner(Long practitionerId) {
        if (practitionerId == null) {
            return Collections.emptyList();
        }
        return repository.findAllByPractitionerId(practitionerId);
    }
}
