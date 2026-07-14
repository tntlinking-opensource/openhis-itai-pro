/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * EMR receive JSON 组装统一接口（各 resource 一实现）。
 *
 * @param <S> Whale 源快照类型
 */
public interface EmrPayloadMapper<S> {

    /**
     * receive 路径段，如 emrPatientInfo。
     *
     * @return resource 名
     */
    String resource();

    /**
     * 幂等键，写入 crb_emr_push_task.business_key。
     *
     * @param source 源快照
     * @param ctx 映射上下文
     * @return 业务键
     */
    String businessKey(S source, CrbMappingContext ctx);

    /**
     * SLA 用的业务完成时刻。
     *
     * @param source 源快照
     * @param ctx 映射上下文
     * @return 操作时刻
     */
    Instant operationTime(S source, CrbMappingContext ctx);

    /**
     * 组装 POST JSON。
     *
     * @param source 源快照
     * @param ctx 映射上下文
     * @return JSON 对象节点
     */
    ObjectNode toPayload(S source, CrbMappingContext ctx);
}
