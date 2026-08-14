/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消药品停嘱校对命令：清空停嘱校对人/时间，不改 {@code status_enum}（仍为 STOPPED）。
 *
 * <p>清空后医生才可取消停嘱（逆流程）。本期不校验操作人是否为原停嘱校对人。</p>
 */
@Getter
@Setter
public class CancelVerifyStopMedicationOrderCommand {
    /** 操作人标识（事件追溯）。 */
    private Long operatorId;
    /** 药品申请主键。 */
    private Long id;
}
