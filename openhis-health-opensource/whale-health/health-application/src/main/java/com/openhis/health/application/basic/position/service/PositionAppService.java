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

package com.openhis.health.application.basic.position.service;

import com.openhis.health.application.basic.position.dto.PositionUserWorkbenchRowDto;
import com.openhis.health.application.basic.position.dto.PractitionerAssignmentRowDto;
import com.openhis.health.application.basic.position.mapper.PositionMapper;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.command.ActivatePractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.command.AssignPractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.command.DeactivatePractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.command.EditPractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.command.RetirePractitionerRoleCommand;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleDomainService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.identity.application.UserAppService;
import com.openhis.whale.identity.application.dto.RoleDto;
import com.openhis.whale.identity.application.dto.UserDto;
import com.openhis.whale.identity.application.dto.UserQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 岗位管理应用服务：医务人员与系统用户维度的「执业岗位」工作台编排入口。
 *
 * <p>领域依据：{@code adm_pr_assign}（执业角色分配）与 DM-ADM-006 业务规则；读侧通过
 * {@link PractitionerRoleQueryService} 与
 * 机构/位置查询服务补全展示字段；写侧委托 {@link PractitionerRoleDomainService}。</p>
 *
 * 请求体为方法参数名与 JSON 字段一一对应（单参数时可直接传对象）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionAppService {

    /** Identity 用户读侧：工作台左侧用户分页与角色列表。 */
    private final UserAppService userAppService;
    /** 医务人员读侧：按 userId 反查主档。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 执业岗位读侧：列出医务人员全部分配行。 */
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    /** 执业岗位写侧：分配、编辑、启停与废弃。 */
    private final PractitionerRoleDomainService practitionerRoleDomainService;
    /** 位置读侧：岗位表单科室下位置下拉。 */
    private final LocationQueryService locationQueryService;
    /** 用户/岗位/位置 DTO 装配。 */
    private final PositionMapper positionMapper;
    /** 写路径操作人门控：须登录后取当前用户 id。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 岗位工作台左侧：分页查询系统用户，并装配系统角色名与绑定医务人员主档（若存在）。
     *
     * <p>为降低误用风险，本方法<strong>强制仅返回已启用用户</strong>：无论调用方是否传入
     * {@link UserQueryRequest#setIsActive(Boolean)}，均会被覆盖为 {@code true}。</p>
     *
     * @param request 分页与关键字条件；允许为 {@code null}（将按空请求处理）
     * @return 分页结果，{@code content} 为 {@link PositionUserWorkbenchRowDto}
     */
    public PagedResult<PositionUserWorkbenchRowDto> pageUsersForWorkbench(UserQueryRequest request) {
        if (request == null) {
            request = new UserQueryRequest();
        }
        // 工作台仅展示可登录的启用用户，忽略调用方传入的 isActive 以免误查停用账号
        request.setIsActive(Boolean.TRUE);
        PagedResult<UserDto> userPage = userAppService.getList(request);
        List<PositionUserWorkbenchRowDto> rows = new ArrayList<>();
        if (userPage.getContent() == null) {
            return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
        }
        // 逐用户补全系统角色名与绑定医务人员，供左侧列表一次渲染
        for (UserDto u : userPage.getContent()) {
            List<RoleDto> roles = userAppService.getRoles(u.getId());
            Practitioner practitioner = practitionerQueryService.findByUserId(u.getId());
            rows.add(positionMapper.toWorkbenchRow(u, roles, practitioner));
        }
        return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
    }

    /**
     * 列出指定医务人员在当前租户下的全部岗位分配行（未逻辑删除的记录），含启用/停用状态。
     *
     * @param practitionerId 医务人员主键；{@code null} 时抛出校验异常
     * @return 按分配主键升序排列的表格行列表
     */
    public List<PractitionerAssignmentRowDto> listPractitionerAssignments(Long practitionerId) {
        if (practitionerId == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        List<PractitionerRole> roles = practitionerRoleQueryService.listAllRolesByPractitioner(practitionerId);
        roles.sort(Comparator.comparing(PractitionerRole::getId, Comparator.nullsLast(Long::compareTo)));
        List<PractitionerAssignmentRowDto> out = new ArrayList<>();
        for (PractitionerRole r : roles) {
            out.add(positionMapper.toAssignmentRow(r));
        }
        return out;
    }

    /**
     * 岗位表单：按科室（机构）加载可选物理位置，仅包含<strong>启用</strong>中的位置，不限形态。
     *
     * <p>选项 {@code label} 与列表「位置」列、{@link #getLocationOptionById(Long)} 使用同一套展示规则（见
     * {@link PositionMapper#toLocationDisplayLabel}）。</p>
     *
     * @param organizationId 机构/科室标识；读侧无数据时返回空表
     * @return 下拉 {@link OptionDto} 列表（已排序）
     */
    public List<OptionDto> listLocationOptionsByOrganization(Long organizationId) {
        return positionMapper.toSortedLocationOptions(locationQueryService.listActiveLocationsByOrganization(organizationId));
    }

    /**
     * 按位置主键返回单条下拉选项，用于当前分配引用的是<strong>非启用</strong>位置时在前端补全展示。
     *
     * <p>与 {@link #listLocationOptionsByOrganization(Long)} 的启用列表取并集后，可避免下拉控件仅显示原始 ID。</p>
     *
     * @param locationId 位置主键；若记录不存在或已被软删过滤排除，返回 {@code null}
     * @return 单条选项；{@code sort} 固定为 0，由前端列表顺序自行决定
     */
    public OptionDto getLocationOptionById(Long locationId) {
        return locationQueryService
            .findOptionalById(locationId)
            .map(loc -> positionMapper.toLocationOption(loc, 0L))
            .orElse(null);
    }

    /**
     * 分配岗位：在校验通过后新增一条 {@code adm_pr_assign}。
     *
     * @param cmd 分配命令；方法内会写入当前用户为 {@code operatorId}
     * @return 新建分配行的主键
     */
    public Long assignPractitionerRole(AssignPractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        return practitionerRoleDomainService.assignRole(cmd);
    }

    /**
     * 编辑岗位：更新科室、物理位置、岗位名称及扩展属性（受领域状态机约束）。
     *
     * @param cmd 编辑命令；方法内会写入当前用户为 {@code operatorId}
     */
    public void editPractitionerRole(EditPractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.editRole(cmd);
    }

    /**
     * 废弃岗位：逻辑删除分配行并记录操作人。
     *
     * @param cmd 废弃命令；方法内会写入当前用户为 {@code operatorId}
     */
    public void retirePractitionerRole(RetirePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.retireRole(cmd);
    }

    /**
     * 启用岗位：将停用的分配行恢复为启用（若领域允许）。
     *
     * @param cmd 启用命令；方法内会写入当前用户为 {@code operatorId}
     */
    public void activatePractitionerRole(ActivatePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.activateRole(cmd);
    }

    /**
     * 停用岗位：将分配行置为停用（不删除历史）。
     *
     * @param cmd 停用命令；方法内会写入当前用户为 {@code operatorId}
     */
    public void deactivatePractitionerRole(DeactivatePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.deactivateRole(cmd);
    }

}
