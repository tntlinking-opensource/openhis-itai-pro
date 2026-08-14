/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 【5302A】人员门诊慢特病审批查询（新）。
 *
 * <p>{@code medType} 可选：不填时平台返回慢病+特病；填 {@code 140104}/{@code 140201} 时按类过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class QueryOpspApproval5302ACommand extends MedicalInfoQueryBaseCommand {

    /** 医疗类别（可选；140104 门诊慢病 / 140201 门诊特病）。 */
    private String medType;
}
