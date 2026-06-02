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

package com.openhis.health.application.basic.department.mapper;

import com.openhis.health.application.basic.department.dto.DepartmentDetailDto;
import com.openhis.health.application.basic.department.dto.DepartmentDto;
import com.openhis.health.application.basic.department.request.CreateDepartmentRequest;
import com.openhis.health.application.basic.department.request.UpdateDepartmentRequest;
import com.openhis.health.domain.administration.organization.command.CreateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.ModifyOrganizationCommand;
import com.openhis.health.domain.administration.organization.entity.Organization;

/**
 * 科室应用层映射器。
 *
 * <p>负责应用层请求对象、领域命令、页面 DTO 之间的转换，保持映射逻辑集中。</p>
 */
public interface DepartmentMapper {

    /**
     * 将领域组织实体转换为科室列表节点 DTO。
     *
     * @param entity 领域组织实体
     * @return 科室列表节点
     */
    DepartmentDto toDto(Organization entity);

    /**
     * 将领域组织实体转换为科室详情 DTO。
     *
     * @param entity 领域组织实体
     * @return 科室详情
     */
    DepartmentDetailDto toDetailDto(Organization entity);

    /**
     * 将新增请求转换为创建组织命令。
     *
     * @param request 新增请求
     * @param operatorId 操作人 ID
     * @return 创建组织命令
     */
    CreateOrganizationCommand toCreateCommand(CreateDepartmentRequest request, Long operatorId);

    /**
     * 将编辑请求转换为修改组织命令。
     *
     * @param request 编辑请求
     * @param operatorId 操作人 ID
     * @return 修改组织命令
     */
    ModifyOrganizationCommand toModifyCommand(UpdateDepartmentRequest request, Long operatorId);
}
