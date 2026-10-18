/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.response;

import com.zxplat.application.outpatient.pharmacy.dispense.dto.PendingDispenseEncounterQueueRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 门诊发药就诊队列分页响应（IF-OPP-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PagePendingDispenseEncounterQueueResponse extends PagedResult<PendingDispenseEncounterQueueRowDto> {
}
