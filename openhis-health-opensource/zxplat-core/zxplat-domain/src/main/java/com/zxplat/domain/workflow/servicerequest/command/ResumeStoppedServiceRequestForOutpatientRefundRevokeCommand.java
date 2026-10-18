/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：恢复因申请而停嘱的诊疗服务申请命令。
 *
 * <p>不走长期医嘱取消停嘱门禁（{@code assertCancelStopAllowed}）；仅要求当前为 {@code STOPPED}，
 * 再调用 {@code fromStopped()} 恢复 ACTIVE/VERIFIED。</p>
 */
@Getter
@Setter
public class ResumeStoppedServiceRequestForOutpatientRefundRevokeCommand {

    /** 因门诊退费申请而停嘱、拟恢复的诊疗申请主键集合。 */
    private Collection<Long> serviceRequestIds;

    /** 操作人标识。 */
    private Long operatorId;
}
