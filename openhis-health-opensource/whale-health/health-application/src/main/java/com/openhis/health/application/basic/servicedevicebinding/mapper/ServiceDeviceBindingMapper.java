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

package com.openhis.health.application.basic.servicedevicebinding.mapper;

import com.openhis.health.application.basic.servicedevicebinding.dto.ServiceDeviceBindingDto;
import com.openhis.health.application.basic.servicedevicebinding.request.SaveServiceDeviceBindingRequest;
import com.openhis.health.domain.custom.servicedevicetemplate.command.CreateBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.command.EditBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;

/**
 * 诊疗耗材绑定：应用 DTO、请求与领域命令之间的装配契约。
 */
public interface ServiceDeviceBindingMapper {

    /**
     * 将服务设备模板查询行投影为绑定列表 DTO。
     *
     * @param model 查询模型；{@code null} 时由实现返回 {@code null}
     * @return 绑定行
     */
    ServiceDeviceBindingDto toDto(ServiceDeviceTemplateQueryModel model);

    /**
     * 组装领域「创建绑定」命令。
     *
     * @param request    保存请求（无 id 分支）
     * @param operatorId 当前操作人
     * @return 创建命令
     */
    CreateBindingCommand toCreateCommand(SaveServiceDeviceBindingRequest request, Long operatorId);

    /**
     * 组装领域「编辑绑定」命令。
     *
     * @param request    保存请求（含 id）
     * @param operatorId 当前操作人
     * @return 编辑命令
     */
    EditBindingCommand toEditCommand(SaveServiceDeviceBindingRequest request, Long operatorId);
}
