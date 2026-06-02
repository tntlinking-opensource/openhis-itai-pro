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

import java.util.List;

/**
 * 执行记录详情模型。
 */
@Getter
@Setter
/**
 * ProcedureDetailQueryModel。
 */
public class ProcedureDetailQueryModel {
    /** 操作。 */
    /**
     * procedure。
     */
    private ProcedureRecordQueryModel procedure;
    /** performers。 */
    /**
     * performers。
     */
    private List<ProcedurePerformerQueryModel> performers;
}
