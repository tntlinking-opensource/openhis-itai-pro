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

package com.openhis.health.application.basic.print.support;

import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 打印模块操作人 / 租户会话解析（禁止 AppService 直注 {@link com.openhis.whale.core.infrastructure.security.ICurrentUser}）。
 */
@Component
@RequiredArgsConstructor
public class PrintOperatorResolver {

  private final CurrentPractitionerResolver currentPractitionerResolver;
  private final ICurrentUser currentUser;

  /**
   * 要求当前会话已选租户。
   *
   * @return 租户主键
   */
  public Long requireTenantId() {
    Long tenantId = TenantUtils.getCurrentTenantId();
    if (tenantId == null) {
      throw new ValidationException("请先选择租户");
    }
    return tenantId;
  }

  /**
   * 当前登录医务人员主键（可空）。
   *
   * @return 操作人 id
   */
  public Long findOperatorIdOrNull() {
    return currentPractitionerResolver.findOperatorIdOrNull();
  }

  /**
   * 当前操作人显示名（可空）。
   *
   * @return 显示名
   */
  public String findOperatorNameOrNull() {
    if (!currentUser.isAuthenticated()) {
      return null;
    }
    return currentUser.getCurrentUserName();
  }

  /**
   * 当前会话院点（可空，本期 confirm 写入 organization_id）。
   *
   * @return 院点 id
   */
  public Long findOrganizationIdOrNull() {
    if (!currentUser.isAuthenticated()) {
      return null;
    }
    return currentUser.getCurrentOrganizationUnitId();
  }
}
