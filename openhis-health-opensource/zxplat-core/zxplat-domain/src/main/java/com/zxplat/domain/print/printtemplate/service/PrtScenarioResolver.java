/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.print.printtemplate.constants.PrintTemplateStatus;
import com.zxplat.domain.print.printtemplate.constants.PrtTenantScope;
import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
import com.zxplat.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务场景码 → 已发布模板解析（替代 PrintBindingResolver）。
 */
@Service
@RequiredArgsConstructor
public class PrtScenarioResolver {

  private final PrtTemplateRepository prtTemplateRepository;

  /**
   * 按场景码取最新 published（租户优先，平台 fallback）。
   *
   * @param scenarioCode 场景码
   * @param tenantId 租户
   * @return published 行或 null
   */
  public PrtTemplate findPublishedByScenarioCode(String scenarioCode, Long tenantId) {
    String code = trimToNull(scenarioCode);
    if (code == null || tenantId == null) {
      return null;
    }
    PrtTemplate tenantRow =
        prtTemplateRepository
            .findFirstByTenantIdAndScenarioCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                tenantId, code, PrintTemplateStatus.PUBLISHED);
    if (tenantRow != null) {
      return tenantRow;
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID != tenantId) {
      return prtTemplateRepository
          .findFirstByTenantIdAndScenarioCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
              PrtTenantScope.PLATFORM_TENANT_ID, code, PrintTemplateStatus.PUBLISHED);
    }
    return null;
  }

  /**
   * 解析场景码 → busNo。
   *
   * @param scenarioCode 场景码
   * @param tenantId 租户
   * @return 有效 busNo
   */
  public String resolveBusNo(String scenarioCode, Long tenantId) {
    PrtTemplate row = findPublishedByScenarioCode(scenarioCode, tenantId);
    if (row == null) {
      throw new BusinessException(
          ErrorCodeConstants.PRT_NOT_FOUND, "未找到场景模板：" + scenarioCode);
    }
    return row.getBusNo();
  }
}
