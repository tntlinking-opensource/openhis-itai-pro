/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.validator;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 打印域会话门控：解析租户、操作人与院点，禁止 AppService 直注 {@link ICurrentUser}。
 *
 * <p>真源落点见规范 B.6.6～B.6.7：跨 print 子模块复用的 Resolver 须在 {@code module.common.validator}，
 * 禁止 {@code application.print.support} 父级包。</p>
 */
@Component
@RequiredArgsConstructor
public class PrintOperatorResolver {

  /** 当前登录医务人员解析。 */
  private final CurrentPractitionerResolver currentPractitionerResolver;

  /** 当前登录用户与会话院点。 */
  private final ICurrentUser currentUser;

  /**
   * 要求当前会话已选租户。
   *
   * @return 租户主键
   * @throws ValidationException 未选租户
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
   * @return 操作人 id；未登录或无映射时 null
   */
  public Long findOperatorIdOrNull() {
    return currentPractitionerResolver.findOperatorIdOrNull();
  }

  /**
   * 当前操作人显示名（可空）。
   *
   * @return 显示名；未登录时 null
   */
  public String findOperatorNameOrNull() {
    if (!currentUser.isAuthenticated()) {
      return null;
    }
    return currentUser.getCurrentUserName();
  }

  /**
   * 当前会话院点（可空，confirm 写入 organization_id）。
   *
   * @return 院点 id；未登录时 null
   */
  public Long findOrganizationIdOrNull() {
    if (!currentUser.isAuthenticated()) {
      return null;
    }
    return currentUser.getCurrentOrganizationUnitId();
  }
}
