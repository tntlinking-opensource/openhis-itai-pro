/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.extension;

import com.openhis.whale.crb.domain.push.command.EmrPushEnqueueCommand;

/**
 * CRB EMR 推送扩展：将 health 领域事件转为异步入队命令。
 */
public interface CrbEmrPushExtension {

    /**
     * 患者建档/变更后推送 emrPatientInfo。
     *
     * @param patientId 患者主键
     * @param tenantId 租户 id
     * @param operatorId 操作人
     */
    void enqueuePatientInfo(Long patientId, Long tenantId, String operatorId);

    /**
     * 通用入队（供其它事件扩展）。
     *
     * @param command 入队命令
     */
    void enqueue(EmrPushEnqueueCommand command);
}
