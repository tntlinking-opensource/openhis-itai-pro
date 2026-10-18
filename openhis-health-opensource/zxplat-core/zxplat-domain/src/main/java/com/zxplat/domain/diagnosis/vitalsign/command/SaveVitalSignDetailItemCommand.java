/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 生命体征明细项命令（DM-DIA-002 v2.1 §5.1.1）。
 */
@Getter
@Setter
public class SaveVitalSignDetailItemCommand {

    /** 术语体系 code（如 {@code vital_signs_code}）。 */
    private String termCode;

    /** 字典项 code。 */
    private String itemCode;

    /** 字典项值。 */
    private String itemValue;

    /** 单位（可空）。 */
    private String units;

    /** 明细备注（可空）。 */
    private String remark;
}
