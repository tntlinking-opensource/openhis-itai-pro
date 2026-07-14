/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.response;

import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundQueueRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 待退费患者队列分页响应（IF-REF-02；B.10.11）。
 *
 * <p>继承 {@link PagedResult}：{@code pageNo} 为 1-based；列表字段为 {@code content}；
 * 总条数为 {@code totalCount}。</p>
 */
public class PageOutpatientRefundQueueResponse extends PagedResult<OutpatientRefundQueueRowDto> {
}
