/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printtemplate.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.print.printtemplate.dto.PrtTemplateDto;
import com.zxplat.application.print.printtemplate.request.SaveDraftRequest;
import com.zxplat.application.print.printtemplate.support.PrintJsonHelper;
import com.zxplat.domain.print.printtemplate.command.PublishTemplateCommand;
import com.zxplat.domain.print.printtemplate.command.SaveDraftCommand;
import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PrintTemplateMapper} 实现：字段直拷 + PrintJson 结构化解析。
 */
@Component
@RequiredArgsConstructor
public class PrintTemplateMapperImpl implements PrintTemplateMapper {

  /** PrintJson 序列化/反序列化。 */
  private final PrintJsonHelper printJsonHelper;

  /**
   * 保存草稿请求转领域命令。
   *
   * @param request 保存请求；调用方保证非 null
   * @param tenantId 当前租户
   * @param busNo 解析后的 busNo
   * @param printJson 已序列化的 PrintJson
   * @param samplePrintDataJson 已序列化的示例数据；可 null
   * @param existingDraftId 已有草稿主键；新建时为 null
   * @return 保存命令
   */
  @Override
  public SaveDraftCommand ConverterSaveDraftCommand(
      SaveDraftRequest request,
      Long tenantId,
      String busNo,
      String printJson,
      String samplePrintDataJson,
      Long existingDraftId) {
    SaveDraftCommand cmd = new SaveDraftCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    cmd.setName(trimToNull(request.getName()));
    cmd.setPrintJson(printJson);
    cmd.setSamplePrintData(samplePrintDataJson);
    cmd.setExistingId(existingDraftId);
    return cmd;
  }

  /**
   * 新建空白草稿命令。
   *
   * @param tenantId 当前租户
   * @param busNo 生成的 busNo
   * @param name 展示名称
   * @param scenarioCode 场景码；可 null
   * @param printJson 空白版式 JSON
   * @return 保存命令
   */
  @Override
  public SaveDraftCommand ConverterBlankDraftCommand(
      Long tenantId, String busNo, String name, String printJson) {
    SaveDraftCommand cmd = new SaveDraftCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    cmd.setName(name);
    cmd.setPrintJson(printJson);
    return cmd;
  }

  /**
   * 发布命令。
   *
   * @param draftId 草稿主键
   * @param tenantId 当前租户
   * @param busNo 模板 busNo
   * @return 发布命令
   */
  @Override
  public PublishTemplateCommand ConverterPublishTemplateCommand(Long draftId, Long tenantId, String busNo) {
    PublishTemplateCommand cmd = new PublishTemplateCommand();
    cmd.setDraftId(draftId);
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    return cmd;
  }

  /**
   * 模板实体转详情 DTO（PrintJson 结构化出参）。
   *
   * @param template 模板行；null 时返回 null
   * @return 详情 DTO
   */
  @Override
  public PrtTemplateDto ConverterPrtTemplateDto(PrtTemplate template) {
    if (template == null) {
      return null;
    }
    PrtTemplateDto dto = new PrtTemplateDto();
    dto.setId(template.getId());
    dto.setBusNo(template.getBusNo());
    dto.setScenarioCode(template.getScenarioCode());
    dto.setName(template.getName());
    dto.setStatus(template.getStatus());
    dto.setVersion(template.getVersion());
    dto.setSchemaVersion(template.getSchemaVersion());
    dto.setPrintJson(printJsonHelper.parseJsonOrNull(template.getPrintJson()));
    dto.setSamplePrintData(printJsonHelper.parseJsonOrNull(template.getSamplePrintData()));
    return dto;
  }
}
