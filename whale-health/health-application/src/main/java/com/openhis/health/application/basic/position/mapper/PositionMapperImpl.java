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

package com.openhis.health.application.basic.position.mapper;

import com.openhis.health.application.basic.position.dto.PositionUserWorkbenchRowDto;
import com.openhis.health.application.basic.position.dto.PractitionerAssignmentRowDto;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.PractitionerBusinessRoleCode;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.identity.application.dto.RoleDto;
import com.openhis.whale.identity.application.dto.UserDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link PositionMapper} 的默认实现。
 *
 * <p>机构名、位置文案依赖查询服务在映射时补全，避免 AppService 内重复拼装。</p>
 */
@Component
public class PositionMapperImpl implements PositionMapper {

    /** 组织读侧：岗位行补全科室名称。 */
    private final OrganizationQueryService organizationQueryService;
    /** 位置读侧：岗位行补全物理位置展示文案。 */
    private final LocationQueryService locationQueryService;

    public PositionMapperImpl(OrganizationQueryService organizationQueryService, LocationQueryService locationQueryService) {
        this.organizationQueryService = organizationQueryService;
        this.locationQueryService = locationQueryService;
    }

    /**
     * 将系统用户、角色与医务人员主档装配为岗位工作台左侧行。
     *
     * @param user          Identity 用户快照
     * @param systemRoles   用户已绑定的系统角色列表
     * @param practitioner  关联医务人员主档，未绑定时可为 {@code null}
     * @return 工作台行 DTO
     */
    @Override
    public PositionUserWorkbenchRowDto toWorkbenchRow(UserDto user, List<RoleDto> systemRoles, Practitioner practitioner) {
        PositionUserWorkbenchRowDto row = new PositionUserWorkbenchRowDto();
        row.setUserId(user.getId());
        row.setUserName(user.getUserName());
        row.setName(user.getName());
        row.setIsActive(user.getIsActive());
        row.setRoleNames(joinSystemRoleDisplayNames(systemRoles));
        // 已绑定医务人员时回填工号与执业状态，供右侧岗位列表关联
        if (practitioner != null) {
            row.setPractitionerId(practitioner.getId());
            row.setPractitionerName(practitioner.getName());
            row.setBusNo(practitioner.getBusNo());
            row.setPractitionerStatusEnum(practitioner.getStatusEnum());
        }
        return row;
    }

    /**
     * 将系统角色列表转为顿号分隔的展示名（优先 displayName，否则 name）。
     *
     * @param roles 系统角色列表
     * @return 展示串；无有效角色时返回空串
     */
    private static String joinSystemRoleDisplayNames(List<RoleDto> roles) {
        if (roles == null || roles.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return roles.stream()
            .map(r -> {
                if (r == null) {
                    return null;
                }
                if (StringUtils.isNotBlank(r.getDisplayName())) {
                    return r.getDisplayName();
                }
                return r.getName();
            })
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.joining("、"));
    }

    /**
     * 将执业岗位分配实体映射为表格行，并补全科室名与位置展示文案。
     *
     * @param role 领域 PractitionerRole 快照
     * @return 岗位分配行 DTO
     */
    @Override
    public PractitionerAssignmentRowDto toAssignmentRow(PractitionerRole role) {
        PractitionerAssignmentRowDto dto = new PractitionerAssignmentRowDto();
        dto.setAssignmentId(role.getId());
        dto.setPractitionerId(role.getPractitionerId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(PractitionerBusinessRoleCode.displayNameOf(role.getRoleCode()));
        dto.setOrgId(role.getOrgId());
        if (role.getOrgId() != null) {
            Organization org = organizationQueryService.loadOrganizationByIdForDisplay(role.getOrgId());
            if (org == null) {
                dto.setOrgName(null);
            } else {
                dto.setOrgName(org.getName());
            }
        }
        dto.setLocationId(role.getLocationId());
        dto.setLocationName(
            locationQueryService
                .findOptionalById(role.getLocationId())
                .map(this::toLocationDisplayLabel)
                .orElse(null));
        dto.setName(role.getName());
        dto.setActiveFlag(role.getActiveFlag());
        dto.setSpecialtyCode(role.getSpecialtyCode());
        dto.setTypeCode(role.getTypeCode());
        return dto;
    }

    /**
     * 将位置列表按显示顺序与名称排序后转为下拉选项。
     *
     * @param locations 机构下启用位置列表
     * @return 已排序的 {@link OptionDto} 列表；入参为空时返回空列表
     */
    @Override
    public List<OptionDto> toSortedLocationOptions(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return List.of();
        }
        List<Location> sorted = new ArrayList<>(locations);
        sorted.sort(
            Comparator.comparing(Location::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Location::getName, Comparator.nullsLast(String::compareTo)));
        List<OptionDto> out = new ArrayList<>();
        long sort = 0L;
        for (Location loc : sorted) {
            out.add(toLocationOption(loc, sort));
            sort++;
        }
        return out;
    }

    /**
     * 将单条位置转为下拉项，label 含形态后缀便于区分同名节点。
     *
     * @param location 位置实体
     * @param sort     下拉排序序号
     * @return 单条 {@link OptionDto}
     */
    @Override
    public OptionDto toLocationOption(Location location, long sort) {
        return new OptionDto(sort, String.valueOf(location.getId()), toLocationDisplayLabel(location));
    }

    /**
     * 生成位置展示标签：名称 + 形态中文（如「一号床（床位）」）。
     *
     * @param location 位置实体
     * @return 展示字符串
     */
    @Override
    public String toLocationDisplayLabel(Location location) {
        String formLabel = location.getFormEnum();
        for (LocationForm f : LocationForm.values()) {
            if (f.getCode().equals(location.getFormEnum())) {
                formLabel = f.getDisplay();
                break;
            }
        }
        String name;
        if (location.getName() != null) {
            name = location.getName();
        } else {
            name = "";
        }
        return name + "（" + formLabel + "）";
    }
}
