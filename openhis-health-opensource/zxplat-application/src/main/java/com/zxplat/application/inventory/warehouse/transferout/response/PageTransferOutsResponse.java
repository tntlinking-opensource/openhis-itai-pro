/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferout.response;

import com.zxplat.application.inventory.warehouse.transferout.dto.TransferOutListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 药库调出单分页列表响应（IF-WHS-TR-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为聚合行列表（每 {@code bus_no} 一条）。</p>
 *
 * <p>{@code totalCount} 为按 {@code bus_no} 去重后的单据总数。</p>
 */
public class PageTransferOutsResponse extends PagedResult<TransferOutListRowDto> {
}
