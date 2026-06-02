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

package com.openhis.health.application.basic.supplier.mapper;

import com.openhis.health.application.basic.supplier.dto.SupplierDto;
import com.openhis.health.application.basic.supplier.request.CreateSupplierRequest;
import com.openhis.health.application.basic.supplier.request.UpdateSupplierRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.custom.supplier.command.MaintainSupplierInfoCommand;
import com.openhis.health.domain.custom.supplier.command.RegisterSupplierCommand;
import com.openhis.health.domain.custom.supplier.entity.Supplier;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;

/**
 * 供应商应用层装配：请求 → 领域命令，查询模型/实体/机构 → DTO 或下拉项。
 */
public interface SupplierMapper {

    /**
     * 创建请求转注册命令。
     *
     * @param request 创建请求
     * @return 注册命令
     */
    RegisterSupplierCommand ConverterRegisterSupplierCommand(CreateSupplierRequest request);

    /**
     * 编辑请求转维护信息命令。
     *
     * @param request 编辑请求
     * @return 维护命令
     */
    MaintainSupplierInfoCommand ConverterMaintainSupplierInfoCommand(UpdateSupplierRequest request);

    /**
     * 领域实体转列表/详情 DTO。
     *
     * @param supplier 供应商聚合根
     * @return DTO
     */
    SupplierDto ConverterSupplierDto(Supplier supplier);

    /**
     * 详情查询模型转 DTO。
     *
     * @param supplier 详情模型
     * @return DTO
     */
    SupplierDto ConverterSupplierDto(SupplierDetailQueryModel supplier);

    /**
     * 列表行模型转 DTO（字段子集）。
     *
     * @param supplier 列表项模型
     * @return DTO
     */
    SupplierDto ConverterSupplierDto(SupplierListItemQueryModel supplier);

    /**
     * 组织机构转下拉选项（value 为机构 id 字符串，ext 为 busNo）。
     *
     * @param supplier 组织节点
     * @return 下拉项
     */
    OptionDto ConverterSupplierDto(Organization supplier);
}
