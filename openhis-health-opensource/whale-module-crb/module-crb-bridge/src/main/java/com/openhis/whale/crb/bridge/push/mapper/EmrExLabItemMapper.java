/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.whale.crb.bridge.lab.pathb.LabResultShapePolicy;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisLabJdbcClient;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisLabOrderRow;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisTestResultRow;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.mapper.support.AbstractSourceIdEmrPayloadMapper;
import com.openhis.whale.crb.bridge.push.support.CrbIdFormatter;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.dict.queries.CrbCodeMapQueryService;
import com.openhis.whale.crb.domain.dict.queries.model.LabItemResolveModel;
import com.openhis.whale.crb.domain.enums.LabResultShape;
import com.openhis.whale.crb.gateway.scheme.push.share.CrbEmrResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.DEFAULT_OPERATOR_ID;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.LAB_SOURCE_EXTERNAL;

/**
 * emrExLabItem 字段映射（Path B：yunlis {@code L_TESTRESULT_ID} = sourceId）。
 */
@Slf4j
@Component
public class EmrExLabItemMapper extends AbstractSourceIdEmrPayloadMapper {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CrbProperties crbProperties;
    private final ObjectProvider<YunlisLabJdbcClient> yunlisLabJdbcClient;
    private final CrbCodeMapQueryService codeMapQueryService;

    public EmrExLabItemMapper(
            CrbProperties crbProperties,
            ObjectProvider<YunlisLabJdbcClient> yunlisLabJdbcClient,
            CrbCodeMapQueryService codeMapQueryService) {
        this.crbProperties = crbProperties;
        this.yunlisLabJdbcClient = yunlisLabJdbcClient;
        this.codeMapQueryService = codeMapQueryService;
    }

    @Override
    public String resource() {
        return CrbEmrResource.EMR_EX_LAB_ITEM;
    }

    @Override
    protected String formatBusinessKey(Long sourceId, CrbMappingContext ctx) {
        return String.valueOf(sourceId);
    }

    @Override
    public Instant operationTime(Long sourceId, CrbMappingContext ctx) {
        if (!isExternalPathB()) {
            return Instant.now();
        }
        YunlisLabJdbcClient client = yunlisLabJdbcClient.getIfAvailable();
        if (client == null) {
            return Instant.now();
        }
        Optional<YunlisTestResultRow> itemOpt = client.findTestResultById(sourceId);
        if (itemOpt.isEmpty()) {
            return Instant.now();
        }
        Optional<YunlisLabOrderRow> orderOpt = client.findLabOrderBySampleNo(itemOpt.get().getSampleNo());
        if (orderOpt.isEmpty() || orderOpt.get().getCheckTime() == null) {
            return Instant.now();
        }
        return orderOpt.get().getCheckTime().atZone(ZONE).toInstant();
    }

    @Override
    public ObjectNode toPayload(Long sourceId, CrbMappingContext ctx) {
        if (!isExternalPathB()) {
            return skeletonPayload(sourceId, ctx);
        }
        YunlisLabJdbcClient client = yunlisLabJdbcClient.getIfAvailable();
        if (client == null) {
            log.warn("emrExLabItem Path B: yunlis client unavailable, sourceId={}", sourceId);
            return null;
        }
        Optional<YunlisTestResultRow> itemOpt = client.findTestResultById(sourceId);
        if (itemOpt.isEmpty()) {
            log.warn("emrExLabItem Path B: testresult missing, sourceId={}", sourceId);
            return null;
        }
        YunlisTestResultRow item = itemOpt.get();
        Optional<YunlisLabOrderRow> orderOpt = client.findLabOrderBySampleNo(item.getSampleNo());
        if (orderOpt.isEmpty()) {
            log.warn("emrExLabItem Path B: laborder missing for sampleNo={}", item.getSampleNo());
            return null;
        }
        return buildExternalPayload(item, orderOpt.get(), ctx);
    }

    private ObjectNode buildExternalPayload(
            YunlisTestResultRow item, YunlisLabOrderRow order, CrbMappingContext ctx) {
        String testId = trimToNull(item.getTestId());
        LabItemResolveModel map = null;
        if (testId != null && ctx.getTenantId() != null) {
            map = codeMapQueryService.resolveLabItem(ctx.getTenantId(), testId);
        }
        if (map == null || !Boolean.TRUE.equals(map.getFound()) || !Boolean.TRUE.equals(map.getPushEnabled())) {
            log.warn("emrExLabItem skip: not in whitelist, testId={}, sourceId={}", testId, item.getTestResultId());
            return null;
        }

        ObjectNode node = newPayloadNode();
        String exLabId = CrbIdFormatter.formatLabId(ctx.getOrgCode(), order.getLabOrderId());
        node.put("id", CrbIdFormatter.formatLabItemId(exLabId, item.getTestResultId()));
        node.put("exLabId", exLabId);
        node.put("itemCode", testId);
        String itemName = trimToNull(item.getChineseName());
        if (itemName == null) {
            itemName = testId;
        }
        node.put("itemName", itemName);

        LabResultShape shape = LabResultShapePolicy.resolve(map.getResultShape(), item.getTestResult());
        if (shape == LabResultShape.QUANT) {
            putQuantFields(node, item);
        } else {
            putQualFields(node, item, map);
        }

        Instant opTime = toInstant(order.getCheckTime());
        String operatorId = firstNonBlank(order.getAuditor(), ctx.getOperatorId(), DEFAULT_OPERATOR_ID);
        CrbMappingContext opCtx = new CrbMappingContext()
                .setOrgCode(ctx.getOrgCode())
                .setOrgName(ctx.getOrgName())
                .setTenantId(ctx.getTenantId())
                .setOperatorId(operatorId);
        putOperationMeta(node, opCtx, opTime);
        return node;
    }

    private static void putQuantFields(ObjectNode node, YunlisTestResultRow item) {
        Double value = LabResultShapePolicy.parseNumber(item.getTestResult());
        if (value != null) {
            node.put("examinationQuantification", value);
        }
        putIfPresent(node, "examinationQuantificationUnit", item.getUnit());
        putIfPresent(node, "examinationQuantificationLower", item.getRefLo());
        putIfPresent(node, "examinationQuantificationUpper", item.getRefHi());
        putIfPresent(node, "examinationQuantificationRi", item.getResultTip());
    }

    private static void putQualFields(ObjectNode node, YunlisTestResultRow item, LabItemResolveModel map) {
        String raw = trimToNull(item.getTestResult());
        if (raw == null) {
            return;
        }
        node.put("sourceExaminationResultCode", raw);
        node.put("sourceExaminationResultName", raw);
        String national = trimToNull(map.getNationalItemCode());
        if (national != null) {
            node.put("examinationResultCode", national);
            node.put("examinationResultName", raw);
        } else {
            node.put("examinationResultCode", raw);
            node.put("examinationResultName", raw);
        }
    }

    private ObjectNode skeletonPayload(Long sourceId, CrbMappingContext ctx) {
        ObjectNode node = newPayloadNode();
        node.put("id", formatBusinessKey(sourceId, ctx));
        putOperationMeta(node, ctx, Instant.now());
        return node;
    }

    private boolean isExternalPathB() {
        String source = trimToNull(crbProperties.getLab().getSource());
        return LAB_SOURCE_EXTERNAL.equals(source);
    }

    private static Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return Instant.now();
        }
        return time.atZone(ZONE).toInstant();
    }

    private static void putIfPresent(ObjectNode node, String key, String value) {
        String v = trimToNull(value);
        if (v != null) {
            node.put(key, v);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }
}
