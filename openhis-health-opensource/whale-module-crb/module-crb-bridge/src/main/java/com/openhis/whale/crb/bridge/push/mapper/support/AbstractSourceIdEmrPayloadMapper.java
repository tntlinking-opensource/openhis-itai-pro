/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.mapper.EmrPayloadMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 以 health 源主键为输入的 Mapper 基类。
 */
public abstract class AbstractSourceIdEmrPayloadMapper implements EmrPayloadMapper<Long> {

    private static final DateTimeFormatter OPERATION_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected ObjectNode newPayloadNode() {
        return objectMapper.createObjectNode();
    }

    protected void putOperationMeta(ObjectNode node, CrbMappingContext ctx, Instant operationTime) {
        if (ctx.getOrgCode() != null) {
            node.put("orgCode", ctx.getOrgCode());
        }
        if (ctx.getOrgName() != null) {
            node.put("orgName", ctx.getOrgName());
        }
        if (ctx.getOperatorId() != null) {
            node.put("operatorId", ctx.getOperatorId());
        }
        if (operationTime != null) {
            node.put("operationTime", OPERATION_TIME_FMT.format(operationTime));
        }
    }

    @Override
    public String businessKey(Long sourceId, CrbMappingContext ctx) {
        return sourceId == null ? "" : formatBusinessKey(sourceId, ctx);
    }

    @Override
    public Instant operationTime(Long sourceId, CrbMappingContext ctx) {
        return Instant.now();
    }

    protected abstract String formatBusinessKey(Long sourceId, CrbMappingContext ctx);
}
