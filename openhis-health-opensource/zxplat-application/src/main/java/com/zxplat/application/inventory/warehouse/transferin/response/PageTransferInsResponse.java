/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferin.response;

import com.zxplat.application.inventory.warehouse.transferin.dto.TransferInListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 药库调入单分页列表响应（IF-WHS-TI-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */

public class PageTransferInsResponse extends PagedResult<TransferInListRowDto> {
}
