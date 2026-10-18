/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.department.mapper;

import com.zxplat.application.basic.department.dto.DepartmentDetailDto;
import com.zxplat.application.basic.department.dto.DepartmentDto;
import com.zxplat.application.basic.department.request.CreateDepartmentRequest;
import com.zxplat.application.basic.department.request.UpdateDepartmentRequest;
import com.zxplat.domain.administration.organization.command.CreateOrganizationCommand;
import com.zxplat.domain.administration.organization.command.ModifyOrganizationCommand;
import com.zxplat.domain.administration.organization.entity.Organization;

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
