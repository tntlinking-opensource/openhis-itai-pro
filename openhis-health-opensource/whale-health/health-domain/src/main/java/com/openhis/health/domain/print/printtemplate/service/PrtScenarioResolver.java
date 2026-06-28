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

package com.openhis.health.domain.print.printtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printtemplate.constants.PrintTemplateStatus;
import com.openhis.health.domain.print.printtemplate.constants.PrtTenantScope;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
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
