/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院登记患者分页检索请求（IF-IP-REG-06）。
 *
 * <p>分页与关键字见 {@link AbstractHealthPageRequest}（B.10.11，{@code pageNo} 从 1 起计）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院登记患者分页检索请求")
public class PagePatientsForSelectRequest extends AbstractHealthPageRequest {
}
