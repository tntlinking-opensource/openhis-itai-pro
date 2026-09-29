/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 临床执行记录（Procedure）跨场景常量：执行参与人职能码等（对齐 DM-CLN-004 §3.2、AS-IP-DOR-012）。
 *
 * <p>手术/麻醉多角色写入 {@code cln_procedure_performer.function_code}；
 * 申请侧人员角色码与本接口同码，禁止院内另造一套。</p>
 */
public interface ClinicalProcedureConstants {

    /**
     * 主执行人职能码：护士站一次执行登记与 Procedure 暂按 1:1 写入 {@code cln_procedure_performer}。
     */
    String PROCEDURE_PERFORMER_FUNCTION_PRIMARY = "PRIMARY";

    /** 手术执行人角色：主刀。 */
    String PROCEDURE_PERFORMER_FUNCTION_SURGEON = "SURGEON";

    /** 手术执行人角色：一助。 */
    String PROCEDURE_PERFORMER_FUNCTION_FIRST_ASSISTANT = "FIRST_ASSISTANT";

    /** 手术执行人角色：二助。 */
    String PROCEDURE_PERFORMER_FUNCTION_SECOND_ASSISTANT = "SECOND_ASSISTANT";

    /** 手术执行人角色：配合护士。 */
    String PROCEDURE_PERFORMER_FUNCTION_NURSE = "NURSE";

    /** 手术/麻醉执行人角色：麻醉医师。 */
    String PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST = "ANESTHETIST";
}
