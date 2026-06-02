/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
