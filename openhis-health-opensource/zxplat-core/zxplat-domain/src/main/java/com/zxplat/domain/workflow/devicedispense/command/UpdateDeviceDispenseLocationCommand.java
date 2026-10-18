/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 护士执行前就地回写耗材发放库房（仅 {@code location_id}）。
 *
 * <p>医生签发已按配置耗材房 FIFO 定价并生成发放；收费完成后护士仅可改发放库房。
 * 禁止改批号、禁止 FIFO、禁止软删重建、禁止动账单。</p>
 */
@Getter
@Setter
public class UpdateDeviceDispenseLocationCommand {

    /** 发放主键。 */
    private Long id;

    /** 目标库房位置主键（adm_location.id）。 */
    private Long locationId;

    /** 操作人主键（审计）。 */
    private Long operatorId;
}
