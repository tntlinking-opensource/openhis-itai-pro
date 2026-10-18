/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 切换就诊主诊断：将指定就诊诊断行（或其所在中医病证组）设为主诊，并清空同就诊其它主诊标记。
 *
 * <p>保证同一就诊至多一个主诊断（西医单行 / 中医病证组视为一组）。</p>
 */
@Getter
@Setter
public class SwitchPrimaryEncounterDiagnosisCommand {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 拟设为主诊的就诊诊断行主键 {@code adm_encounter_diagnosis.id}。 */
    private Long encounterDiagnosisId;

    /** 操作人主键。 */
    private Long operatorId;
}
