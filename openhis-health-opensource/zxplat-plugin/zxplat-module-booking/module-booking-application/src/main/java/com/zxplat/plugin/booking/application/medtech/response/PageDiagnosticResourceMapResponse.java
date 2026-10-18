/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技资源映射分页响应（7.7）。
 */
@Getter
@Setter
public class PageDiagnosticResourceMapResponse extends PagedResult<DiagnosticResourceMapDto> {
}
