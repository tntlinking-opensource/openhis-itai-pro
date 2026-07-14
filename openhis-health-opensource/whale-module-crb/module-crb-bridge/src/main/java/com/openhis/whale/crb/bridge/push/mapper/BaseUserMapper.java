/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.mapper.support.AbstractSourceIdEmrPayloadMapper;
import com.openhis.whale.crb.bridge.push.support.CrbIdFormatter;
import org.springframework.stereotype.Component;

/**
 * baseUser 字段映射（P1/P2 骨架，待 EMR/health 快照接入）。
 */
@Component
public class BaseUserMapper extends AbstractSourceIdEmrPayloadMapper {

    @Override
    public String resource() {
        return "baseUser";
    }

    @Override
    protected String formatBusinessKey(Long sourceId, CrbMappingContext ctx) {
        return CrbIdFormatter.formatUserId(sourceId);
    }

    @Override
    public ObjectNode toPayload(Long sourceId, CrbMappingContext ctx) {
        ObjectNode node = newPayloadNode();
        node.put("id", formatBusinessKey(sourceId, ctx));
        putOperationMeta(node, ctx, operationTime(sourceId, ctx));
        return node;
    }
}
