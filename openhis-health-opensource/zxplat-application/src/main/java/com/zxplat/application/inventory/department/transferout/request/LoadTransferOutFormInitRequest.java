/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.transferout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室调出页初始化请求（IF-DEPT-TR-01）。
 *
 * <p>机构维度由应用层 {@link com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver} 解析，请求体不得承载机构主键作为权威来源。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class LoadTransferOutFormInitRequest {
}
