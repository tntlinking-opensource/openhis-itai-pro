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

package com.openhis.health.domain.clinical.procedure.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医嘱执行单分页入参（查 {@code cln_procedure}；替代 RM {@code OrderExecutionSheetPageQuery}）。
 */
@Getter
@Setter
public class OrderExecutionSheetPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    private Long procedureId;

    private Long requestId;

    private Long encounterId;

    private String statusEnum;

    private String categoryEnum;

    private Long refundId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
