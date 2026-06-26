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

package com.openhis.health.application.basic.printbinding.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.print.support.PrintOperatorResolver;
import com.openhis.health.application.basic.printbinding.dto.BindingConfigDto;
import com.openhis.health.application.basic.printbinding.dto.BindingItemDto;
import com.openhis.health.application.basic.printbinding.dto.TemplateCandidateDto;
import com.openhis.health.application.basic.printbinding.request.SaveBindingConfigRequest;
import com.openhis.health.domain.custom.printbinding.command.SaveTenantBindingsCommand;
import com.openhis.health.domain.custom.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.custom.printbinding.service.PrintBindingResolver;
import com.openhis.health.domain.custom.printtemplate.constants.PrintTemplateStatus;
import com.openhis.health.domain.custom.printtemplate.constants.PrtTenantScope;
import com.openhis.health.domain.custom.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.custom.printtemplate.service.PrtTemplateManager;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务场景模板绑定应用服务（IF-BAS-PRT-21～22）。
 */
@Service
@RequiredArgsConstructor
public class PrintBindingAppService {

  private final PrintBindingResolver printBindingResolver;
  private final PrtTemplateManager prtTemplateManager;
  private final PrintOperatorResolver printOperatorResolver;

  /**
   * 加载绑定配置（IF-BAS-PRT-21）。
   *
   * @return 场景绑定与候选模板
   */
  public BindingConfigDto loadConfig() {
    Long tenantId = printOperatorResolver.requireTenantId();
    List<PrtPrintBinding> definitions = printBindingResolver.listDefinitions();
    List<PrtPrintBinding> overrides = printBindingResolver.listOverridesForTenant(tenantId);
    Map<String, PrtPrintBinding> overrideByCode = new HashMap<>();
    for (PrtPrintBinding row : overrides) {
      overrideByCode.put(row.getBindingCode(), row);
    }
    BindingConfigDto dto = new BindingConfigDto();
    for (PrtPrintBinding definition : definitions) {
      BindingItemDto item = new BindingItemDto();
      item.setBindingCode(definition.getBindingCode());
      item.setName(definition.getName());
      PrtPrintBinding override = overrideByCode.get(definition.getBindingCode());
      if (override != null) {
        item.setBusNo(override.getBusNo());
        item.setTenantOverride(Boolean.TRUE);
      } else {
        item.setBusNo(definition.getBusNo());
        item.setTenantOverride(Boolean.FALSE);
      }
      dto.getBindings().add(item);
    }
    List<PrtTemplate> merged = prtTemplateManager.listMergedForTenant(tenantId, false);
    for (PrtTemplate row : merged) {
      if (!PrintTemplateStatus.PUBLISHED.equals(row.getStatus())
          && !PrintTemplateStatus.DRAFT.equals(row.getStatus())) {
        continue;
      }
      if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId
          && PrintTemplateStatus.DRAFT.equals(row.getStatus())) {
        continue;
      }
      TemplateCandidateDto candidate = new TemplateCandidateDto();
      candidate.setBusNo(row.getBusNo());
      candidate.setName(row.getName());
      candidate.setStatus(row.getStatus());
      dto.getCandidates().add(candidate);
    }
    return dto;
  }

  /**
   * 保存绑定配置（IF-BAS-PRT-22）。
   *
   * @param request bindingCode→busNo 全量映射
   * @return 保存后配置快照
   */
  public BindingConfigDto saveConfig(SaveBindingConfigRequest request) {
    if (request == null || request.getBindings() == null || request.getBindings().isEmpty()) {
      throw new ValidationException("绑定配置不能为空");
    }
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
      PrtTemplate published = prtTemplateManager.findPublished(busNo, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
      }
      normalized.put(bindingCode, busNo);
    }
    SaveTenantBindingsCommand cmd = new SaveTenantBindingsCommand();
    cmd.setTenantId(tenantId);
    cmd.setBindingCodeToBusNo(normalized);
    printBindingResolver.saveTenantBindings(cmd);
    return loadConfig();
  }
}
