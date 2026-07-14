/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferin.response;

import com.openhis.health.application.inventory.pharmacy.transferin.dto.TransferInListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 药房调入单分页列表响应（IF-PHA-TI-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageTransferInsResponse extends PagedResult<TransferInListRowDto> {
}
