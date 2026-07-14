/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push.command;

import com.openhis.whale.crb.domain.enums.EmrPushSlaTier;
import com.openhis.whale.crb.domain.enums.PushOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * EMR 推送入队命令（由 adapter 事件监听器构造）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class EmrPushEnqueueCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** receive 路径段，如 emrPatientInfo。 */
    private String resource;

    /** 幂等键，写入 crb_emr_push_task.business_key。 */
    private String businessKey;

    /** SLA 分档。 */
    private EmrPushSlaTier slaTier;

    /** UPSERT 或 DELETE。 */
    private PushOperation operation;

    /** 业务完成时刻，用于 SLA due_at 计算。 */
    private Instant operationTime;

    /** 操作人 id 字符串（写入 Mapper 上下文）。 */
    private String operatorId;

    /** 源实体主键（health 侧），便于 Worker 加载快照。 */
    private Long sourceId;
}
