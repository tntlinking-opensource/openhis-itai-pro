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

package com.openhis.health.application.print.printbinding.mapper;

import com.openhis.health.application.print.printbinding.dto.BindingItemDto;
import com.openhis.health.application.print.printbinding.dto.TemplateCandidateDto;
import com.openhis.health.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import java.util.Map;

/**
 * 业务场景模板绑定应用层装配：实体 → DTO，保存请求 → Command。
 */
public interface PrintBindingMapper {

  /**
   * 出厂定义 + 租户 override 转绑定行 DTO。
   *
   * @param definition 平台出厂 binding 定义
   * @param tenantOverride 租户 override；无 override 时为 null
   * @return 绑定行
   */
  BindingItemDto ConverterBindingItemDto(PrtPrintBinding definition, PrtPrintBinding tenantOverride);

  /**
   * 模板物理行转候选下拉 DTO。
   *
   * @param template 模板行
   * @return 候选项
   */
  TemplateCandidateDto ConverterTemplateCandidateDto(PrtTemplate template);

  /**
   * 全量 bindingCode→busNo 映射转保存命令。
   *
   * @param tenantId 当前租户
   * @param bindingCodeToBusNo 已校验的映射
   * @return 保存命令
   */
  SaveTenantBindingsCommand ConverterSaveTenantBindingsCommand(Long tenantId, Map<String, String> bindingCodeToBusNo);
}
