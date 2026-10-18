/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.gainloss.response;

import com.zxplat.application.inventory.warehouse.gainloss.dto.GainLossListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 药库损益单分页列表响应（IF-WHS-GL-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageGainLosssResponse extends PagedResult<GainLossListRowDto> {
}
