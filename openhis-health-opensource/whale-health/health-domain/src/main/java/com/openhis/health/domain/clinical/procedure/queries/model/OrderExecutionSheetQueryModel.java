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

package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医嘱执行单查询行（查 {@code cln_procedure} + 来源请求/执行人装配；替代 {@code OrderExecutionSheetReadModel}）。
 */
@Getter
@Setter
public class OrderExecutionSheetQueryModel {

    private Long procedureId;
    private Long requestId;
    private String requestTable;
    private String statusEnum;
    private String categoryEnum;
    private Long patientId;
    private Long encounterId;
    private LocalDateTime occurrenceTime;
    private LocalDateTime recordedTime;
    private Long recorderId;
    private String resultText;
    private String orderContentJson;
    private String performerListJson;
}
