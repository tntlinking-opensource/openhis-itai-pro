/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 改服务申请业务时间命令（开立或停嘱；禁止改签发；AS-IP-DOR-003 IF-IP-DOR-26）。
 */
@Getter
@Setter
public class ChangeServiceRequestBusinessTimeCommand {

    /** 操作人主键（审计用；不落「改时人」列）。 */
    private Long operatorId;

    /** 服务申请主键。 */
    private Long id;

    /**
     * 时间种类 code：{@code OPENED} / {@code STOP}
     * （{@link com.zxplat.domain.share.enums.RequestBusinessTimeKind}）。
     */
    private String timeKind;

    /** 新业务时间。 */
    private LocalDateTime newTime;
}
