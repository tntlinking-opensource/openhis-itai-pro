/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printbinding.mapper;

import com.zxplat.application.print.printbinding.dto.BindingItemDto;
import com.zxplat.application.print.printbinding.dto.TemplateCandidateDto;
import com.zxplat.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.zxplat.domain.print.printbinding.entity.PrtPrintBinding;
import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
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
