/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContextFactory;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.gateway.scheme.push.share.PushSchemeJsonSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * EmrPayloadMapper 注册表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmrPayloadMapperRegistry {

    private final List<EmrPayloadMapper<Long>> mappers;
    private final CrbMappingContextFactory mappingContextFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * scheme 模型 → ObjectNode（P10-C/D 参考 mapper 使用）。
     */
    public ObjectNode toObjectNode(Object payload) {
        return PushSchemeJsonSupport.toObjectNode(payload);
    }

    public Optional<String> buildPayloadJson(CrbEmrPushTask task) {
        if (task == null || task.getResource() == null || task.getSourceId() == null) {
            return Optional.empty();
        }
        for (EmrPayloadMapper<Long> mapper : mappers) {
            if (!task.getResource().equals(mapper.resource())) {
                continue;
            }
            CrbMappingContext ctx = mappingContextFactory.create(task.getOperatorId());
            var payload = mapper.toPayload(task.getSourceId(), ctx);
            if (payload == null) {
                log.warn("Mapper returned null payload, resource={}, sourceId={}",
                        task.getResource(), task.getSourceId());
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.writeValueAsString(payload));
            } catch (Exception ex) {
                log.warn("Serialize payload failed, resource={}", task.getResource(), ex);
                return Optional.empty();
            }
        }
        log.debug("No mapper registered for resource={}", task.getResource());
        return Optional.empty();
    }
}
