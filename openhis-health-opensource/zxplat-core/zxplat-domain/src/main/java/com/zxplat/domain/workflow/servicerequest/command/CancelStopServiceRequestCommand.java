/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消诊疗服务申请停嘱命令（IF-IP-DOR-25）。
 */
@Getter
@Setter
public class CancelStopServiceRequestCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 服务申请标识。 */
    private Long id;
}
