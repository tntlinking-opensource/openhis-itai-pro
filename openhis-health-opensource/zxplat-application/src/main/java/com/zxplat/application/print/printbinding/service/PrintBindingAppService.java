/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printbinding.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.print.printbinding.dto.BindingConfigDto;
import com.zxplat.application.print.printbinding.dto.BindingItemDto;
import com.zxplat.application.print.printbinding.dto.TemplateCandidateDto;
import com.zxplat.application.print.printbinding.mapper.PrintBindingMapper;
import com.zxplat.application.print.printbinding.request.SaveBindingConfigRequest;
import com.zxplat.application.shared.validator.PrintOperatorResolver;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.zxplat.domain.print.printbinding.entity.PrtPrintBinding;
import com.zxplat.domain.print.printbinding.service.PrtPrintBindingDomainService;
import com.zxplat.domain.print.printbinding.service.PrtPrintBindingQueryService;
import com.zxplat.domain.share.enums.PrintTemplateStatus;
import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
import com.zxplat.domain.print.printtemplate.service.PrtTemplateQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务场景模板绑定应用服务（AS-BAS-PRT，IF-BAS-PRT-21～22）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>加载平台出厂 binding 与租户 override 合并视图；</li>
 *   <li>保存时校验目标 busNo 须存在 published 模板；</li>
 *   <li>出参/命令经 {@link PrintBindingMapper} 装配。</li>
 * </ul>
 *
 * <p>由 AppServiceControllerRegistrar 暴露 REST，本类不含 Controller。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintBindingAppService {

  /** binding 读侧：出厂定义与租户 override。 */
  private final PrtPrintBindingQueryService prtPrintBindingQueryService;

  /** binding 写侧：全量保存租户 override。 */
  private final PrtPrintBindingDomainService prtPrintBindingDomainService;

  /** 模板读侧：校验 published 存在性与候选列表。 */
  private final PrtTemplateQueryService prtTemplateQueryService;

  /** 会话租户解析。 */
  private final PrintOperatorResolver printOperatorResolver;

  /** binding 实体 → DTO 与 SaveCommand 装配。 */
  private final PrintBindingMapper printBindingMapper;

  /**
   * 加载绑定配置（IF-BAS-PRT-21）。
   *
   * @return 场景绑定行与可选模板候选列表
   */
  public BindingConfigDto loadConfig() {
    // Gate：须已选租户。
    Long tenantId = printOperatorResolver.requireTenantId();
    // Pre：平台 seed 定义 + 租户 override 合并。
    List<PrtPrintBinding> definitions = prtPrintBindingQueryService.listDefinitions();
    List<PrtPrintBinding> overrides = prtPrintBindingQueryService.listOverridesForTenant(tenantId);
    Map<String, PrtPrintBinding> overrideByCode = new HashMap<>();
    for (PrtPrintBinding row : overrides) {
      overrideByCode.put(row.getBindingCode(), row);
    }
    BindingConfigDto dto = new BindingConfigDto();
    for (PrtPrintBinding definition : definitions) {
      PrtPrintBinding override = overrideByCode.get(definition.getBindingCode());
      BindingItemDto item = printBindingMapper.ConverterBindingItemDto(definition, override);
      dto.getBindings().add(item);
    }
    // Pre：候选模板仅 draft/published，平台租户不展示 draft。
    List<PrtTemplate> merged = prtTemplateQueryService.listMergedForTenant(tenantId, false);
    for (PrtTemplate row : merged) {
      if (!PrintTemplateStatus.PUBLISHED.sameAs(row.getStatus())
          && !PrintTemplateStatus.DRAFT.sameAs(row.getStatus())) {
        continue;
      }
      if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId
          && PrintTemplateStatus.DRAFT.sameAs(row.getStatus())) {
        continue;
      }
      TemplateCandidateDto candidate = printBindingMapper.ConverterTemplateCandidateDto(row);
      dto.getCandidates().add(candidate);
    }
    return dto;
  }

  /**
   * 保存绑定配置（IF-BAS-PRT-22）：全量覆盖租户 bindingCode→busNo。
   *
   * @param request bindingCode→busNo 全量映射
   * @return 保存后配置快照（同 loadConfig）
   * @throws ValidationException 绑定为空或字段缺失
   * @throws BusinessException 目标 busNo 无 published 模板
   */
  public BindingConfigDto saveConfig(SaveBindingConfigRequest request) {
    if (request == null || request.getBindings() == null || request.getBindings().isEmpty()) {
      throw new ValidationException("绑定配置不能为空");
    }
    // Gate：须已选租户。
    Long tenantId = printOperatorResolver.requireTenantId();
    Map<String, String> normalized = new HashMap<>();
    for (Map.Entry<String, String> entry : request.getBindings().entrySet()) {
      String bindingCode = trimToNull(entry.getKey());
      String busNo = trimToNull(entry.getValue());
      if (bindingCode == null) {
        throw new ValidationException("bindingCode 不能为空");
      }
      if (busNo == null) {
        throw new ValidationException("请为所有场景选择有效模板");
      }
      // Gate：每个 busNo 须在当前租户下可解析为 published。
      PrtTemplate published = prtTemplateQueryService.findPublished(busNo, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
      }
      normalized.put(bindingCode, busNo);
    }
    // Cmd：Mapper 装配命令并全量保存租户 override。
    SaveTenantBindingsCommand cmd = printBindingMapper.ConverterSaveTenantBindingsCommand(tenantId, normalized);
    prtPrintBindingDomainService.saveTenantBindings(cmd);
    return loadConfig();
  }
}
