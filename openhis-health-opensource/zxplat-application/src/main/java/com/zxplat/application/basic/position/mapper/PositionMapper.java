/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.mapper;

import com.zxplat.application.basic.position.dto.PositionUserWorkbenchRowDto;
import com.zxplat.application.basic.position.dto.PractitionerAssignmentRowDto;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitionerrole.entity.PractitionerRole;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.identity.application.dto.RoleDto;
import com.zxplat.framework.identity.application.dto.UserDto;

import java.util.List;

/**
 * 岗位（医务人员执业角色分配）应用层映射器。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>将身份侧 {@link UserDto}、系统角色、医务人员主档装配为工作台左侧行 {@link PositionUserWorkbenchRowDto}；</li>
 *   <li>将领域聚合 {@link PractitionerRole} 转为岗位分配表格行 {@link PractitionerAssignmentRowDto}（含机构名、位置展示文案）；</li>
 *   <li>将 {@link Location} 转为前端下拉 {@link OptionDto} 及与表格一致的「名称（形态）」展示串。</li>
 * </ul>
 *
 * <p>不含命令组装与持久化编排，仅做只读模型转换。</p>
 */
public interface PositionMapper {

    /**
     * 装配岗位工作台左侧用户行：账号信息 + 系统角色顿号拼接 + 医务人员主档（若有）。
     *
     * @param user          用户列表中的用户行，不可为 {@code null}
     * @param systemRoles   该用户在当前租户下的系统角色列表，可为空表
     * @param practitioner  与用户绑定的医务人员主档；未绑定时为 {@code null}
     * @return 工作台左侧一行展示数据
     */
    PositionUserWorkbenchRowDto toWorkbenchRow(UserDto user, List<RoleDto> systemRoles, Practitioner practitioner);

    /**
     * 将单条 {@code adm_pr_assign} 领域行转为列表/编辑用 DTO。
     *
     * <p>会解析机构名称、位置展示标签（与下拉选项文案一致）。</p>
     *
     * @param role 岗位分配聚合根，不可为 {@code null}
     * @return 表格行
     */
    PractitionerAssignmentRowDto toAssignmentRow(PractitionerRole role);

    /**
     * 将位置集合按展示顺序排序后转为下拉选项列表。
     *
     * <p>排序规则：{@code displayOrder} 升序，再按 {@code name} 升序；每条选项的 label 与列表「位置」列一致。</p>
     *
     * @param locations 通常为某机构下启用中的物理位置；可为空表
     * @return 带递增 {@code sort} 的 {@link OptionDto} 列表
     */
    List<OptionDto> toSortedLocationOptions(List<Location> locations);

    /**
     * 单条位置转下拉项。
     *
     * @param location 位置实体，不可为 {@code null}
     * @param sort     选项排序序号（前端可用于稳定排序）
     * @return value 为位置主键字符串、label 为 {@link #toLocationDisplayLabel(Location)}
     */
    OptionDto toLocationOption(Location location, long sort);

    /**
     * 位置在岗位工作台中的统一展示文案：{@code 名称（形态中文）}。
     *
     * <p>形态编码解析失败时回退为存储的枚举编码字符串。</p>
     *
     * @param location 位置实体，不可为 {@code null}
     * @return 展示用标签，与下拉、表格列保持一致
     */
    String toLocationDisplayLabel(Location location);
}
