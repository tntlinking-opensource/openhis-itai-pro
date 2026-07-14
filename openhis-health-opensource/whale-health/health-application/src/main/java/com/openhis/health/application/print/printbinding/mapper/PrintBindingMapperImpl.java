/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printbinding.mapper;

import com.openhis.health.application.print.printbinding.dto.BindingItemDto;
import com.openhis.health.application.print.printbinding.dto.TemplateCandidateDto;
import com.openhis.health.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link PrintBindingMapper} 实现：字段直拷。
 */
@Component
public class PrintBindingMapperImpl implements PrintBindingMapper {

  /**
   * 出厂定义 + 租户 override 转绑定行 DTO。
   *
   * @param definition 平台出厂 binding 定义
   * @param tenantOverride 租户 override；无 override 时为 null
   * @return 绑定行
   */
  @Override
  public BindingItemDto ConverterBindingItemDto(
      PrtPrintBinding definition, PrtPrintBinding tenantOverride) {
    BindingItemDto item = new BindingItemDto();
    item.setBindingCode(definition.getBindingCode());
    item.setName(definition.getName());
    if (tenantOverride != null) {
      // 租户已覆盖出厂默认 binding。
      item.setBusNo(tenantOverride.getBusNo());
      item.setTenantOverride(Boolean.TRUE);
    } else {
      item.setBusNo(definition.getBusNo());
      item.setTenantOverride(Boolean.FALSE);
    }
    return item;
  }

  /**
   * 模板物理行转候选下拉 DTO。
   *
   * @param template 模板行
   * @return 候选项
   */
  @Override
  public TemplateCandidateDto ConverterTemplateCandidateDto(PrtTemplate template) {
    TemplateCandidateDto candidate = new TemplateCandidateDto();
    candidate.setBusNo(template.getBusNo());
    candidate.setName(template.getName());
    candidate.setStatus(template.getStatus());
    return candidate;
  }

  /**
   * 全量 bindingCode→busNo 映射转保存命令。
   *
   * @param tenantId 当前租户
   * @param bindingCodeToBusNo 已校验的映射
   * @return 保存命令
   */
  @Override
  public SaveTenantBindingsCommand ConverterSaveTenantBindingsCommand(
      Long tenantId, Map<String, String> bindingCodeToBusNo) {
    SaveTenantBindingsCommand cmd = new SaveTenantBindingsCommand();
    cmd.setTenantId(tenantId);
    cmd.setBindingCodeToBusNo(bindingCodeToBusNo);
    return cmd;
  }
}
