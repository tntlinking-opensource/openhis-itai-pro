/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊医生站就诊管理共享常量（AS-OP-DOR-001 IF-DOR-06 取消接诊等）。
 */
public interface DoctorStationEncounterManageConstants {

    /**
     * EX-DOR-105：取消接诊时本诊仍有未删病历、诊断或医生开立医嘱。
     *
     * <p>工程 {@code errorCode} 仍用 {@link ErrorCodeConstants#ENCOUNTER_RULE_CONFLICT}；本常量为 AS 语义码。</p>
     */
    String EX_DOR_105 = "EX-DOR-105";

    /**
     * 取消接诊门禁：本诊临床痕迹未清空时的固定提示。
     *
     * <p>与账单无关；须医生先删病历、诊断与医嘱（含药/耗/诊疗），回到未接诊临床空态后再取消接诊。</p>
     */
    String CANCEL_RECEPTION_CLINICAL_TRACES_REMAINING_MESSAGE =
        "取消接诊前须先删除本诊全部病历、诊断与医嘱，请清空后再试";
}
