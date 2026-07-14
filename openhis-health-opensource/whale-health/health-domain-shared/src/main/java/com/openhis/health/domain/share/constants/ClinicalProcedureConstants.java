/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
