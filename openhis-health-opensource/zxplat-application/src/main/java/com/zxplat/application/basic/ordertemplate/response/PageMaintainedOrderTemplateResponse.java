/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import com.zxplat.application.basic.ordertemplate.dto.OrderTemplateHeaderRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 维护端组套模板分页响应（IF-BAS-OTL-02）。
 */
@Schema(description = "维护端组套模板分页响应")
public class PageMaintainedOrderTemplateResponse extends PagedResult<OrderTemplateHeaderRowDto> {
}
