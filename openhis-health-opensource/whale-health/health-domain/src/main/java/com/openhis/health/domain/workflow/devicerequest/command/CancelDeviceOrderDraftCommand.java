/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 废弃耗材医嘱草稿命令（DRAFT → CANCELLED，不软删）。
 */
@Getter
@Setter
public class CancelDeviceOrderDraftCommand {

    /** 耗材申请主键。 */
    private Long id;

    /** 操作人主键。 */
    private Long operatorId;

    /** 取消/废弃原因。 */
    private String cancelReason;
}
