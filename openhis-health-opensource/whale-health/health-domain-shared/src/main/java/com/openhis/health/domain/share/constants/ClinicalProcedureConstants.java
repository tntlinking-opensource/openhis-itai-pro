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

package com.openhis.health.domain.share.constants;

/**
 * 临床执行记录（Procedure）跨场景常量：执行参与人职能码等（对齐 DM-CLN-004 §3.2）。
 */
public interface ClinicalProcedureConstants {

    /**
     * 主执行人职能码：护士站一次执行登记与 Procedure 暂按 1:1 写入 {@code cln_procedure_performer}。
     */
    String PROCEDURE_PERFORMER_FUNCTION_PRIMARY = "PRIMARY";
}
