/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 医嘱签发批次目标中性规格（门诊/住院医生站皮试联动共用）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AdviceSignTargetSpec {

    /** 产品定义表名。 */
    private String productTableName;

    /** 医嘱申请主键。 */
    private Long requestId;
}
