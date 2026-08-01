/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import com.openhis.whale.crb.bridge.extension.CrbEmrPushExtension;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.dict.queries.CrbCodeMapQueryService;
import com.openhis.whale.crb.domain.dict.queries.model.LabItemResolveModel;
import com.openhis.whale.crb.domain.enums.EmrPushSlaTier;
import com.openhis.whale.crb.domain.enums.PushOperation;
import com.openhis.whale.crb.domain.push.command.EmrPushEnqueueCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.DEFAULT_OPERATOR_ID;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.LAB_TYPE_PHYSICAL_EXAM;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_DONE;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_ERROR;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_MESSAGE_MAX_LENGTH;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_SKIP;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.RECEIVE_FLAG_AUDITED;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.RECEIVE_FLAG_PRINTED;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.RESOURCE_EMR_EX_LAB;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.RESOURCE_EMR_EX_LAB_ITEM;

/**
 * 消费 yunlis Outbox：过滤体检/白名单、APPLYNO 反查、入队 emrExLab/Item。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.crb.lab.yunlis.enabled", havingValue = "true")
public class YunlisLabOutboxConsumer {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final YunlisLabJdbcClient jdbcClient;
    private final YunlisApplyNoServiceRequestResolver applyNoResolver;
    private final CrbCodeMapQueryService codeMapQueryService;
    private final CrbEmrPushExtension pushExtension;
    private final CrbProperties crbProperties;

    /**
     * 拉取并消费一批 PENDING Outbox。
     */
    public void consumeBatch() {
        Long tenantId = crbProperties.getLab().getTenantId();
        if (tenantId == null) {
            log.warn("Yunlis outbox skip: openhis.crb.lab.tenant-id 未配置");
            return;
        }
        List<YunlisOutboxRow> pending = jdbcClient.listPendingOutbox();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Yunlis outbox consume start, size={}", pending.size());
        for (YunlisOutboxRow row : pending) {
            consumeOne(tenantId, row);
        }
    }

    private void consumeOne(Long tenantId, YunlisOutboxRow outbox) {
        try {
            processRow(tenantId, outbox);
        } catch (Exception ex) {
            log.warn("Yunlis outbox ERROR labOrderId={}", outbox.getLabOrderId(), ex);
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_ERROR, truncate(ex.getMessage()));
        }
    }

    private void processRow(Long tenantId, YunlisOutboxRow outbox) {
        Optional<YunlisLabOrderRow> orderOpt = jdbcClient.findLabOrderById(outbox.getLabOrderId());
        if (orderOpt.isEmpty()) {
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_SKIP, "laborder not found");
            return;
        }
        YunlisLabOrderRow order = orderOpt.get();
        if (LAB_TYPE_PHYSICAL_EXAM.equals(trimToNull(order.getType()))) {
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_SKIP, "TYPE=04 physical exam");
            return;
        }
        if (!isConsumableReceiveFlag(order.getReceiveFlag())) {
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_SKIP, "RECEIVEFLAG not 6/7");
            return;
        }

        List<YunlisTestResultRow> results = jdbcClient.listTestResultsBySampleNo(order.getSampleNo());
        List<YunlisTestResultRow> whitelisted = filterWhitelist(tenantId, results);
        if (whitelisted.isEmpty()) {
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_SKIP, "no whitelist lab item");
            return;
        }

        String applyNo = firstNonBlank(order.getBarcode(), order.getApplyNo());
        Optional<YunlisApplyNoResolveResult> resolved = applyNoResolver.resolve(applyNo);
        if (resolved.isEmpty()) {
            // 申请单尚未回写 Whale 时可重试
            jdbcClient.markOutbox(outbox.getId(), OUTBOX_ERROR, "APPLYNO not resolved: " + applyNo);
            return;
        }

        Instant operationTime = toInstant(order.getCheckTime());
        String operatorId = firstNonBlank(order.getAuditor(), DEFAULT_OPERATOR_ID);

        pushExtension.enqueue(new EmrPushEnqueueCommand()
                .setTenantId(tenantId)
                .setResource(RESOURCE_EMR_EX_LAB)
                .setBusinessKey(String.valueOf(order.getLabOrderId()))
                .setSlaTier(EmrPushSlaTier.TWO_HOUR)
                .setOperation(PushOperation.UPSERT)
                .setOperationTime(operationTime)
                .setOperatorId(operatorId)
                .setSourceId(order.getLabOrderId()));

        for (YunlisTestResultRow item : whitelisted) {
            String itemKey = order.getLabOrderId() + "_" + item.getTestResultId();
            pushExtension.enqueue(new EmrPushEnqueueCommand()
                    .setTenantId(tenantId)
                    .setResource(RESOURCE_EMR_EX_LAB_ITEM)
                    .setBusinessKey(itemKey)
                    .setSlaTier(EmrPushSlaTier.TWO_HOUR)
                    .setOperation(PushOperation.UPSERT)
                    .setOperationTime(operationTime)
                    .setOperatorId(operatorId)
                    .setSourceId(item.getTestResultId()));
        }

        jdbcClient.markOutbox(
                outbox.getId(),
                OUTBOX_DONE,
                "enqueued head+items=" + whitelisted.size() + " matchedBy=" + resolved.get().getMatchedBy());
    }

    private List<YunlisTestResultRow> filterWhitelist(Long tenantId, List<YunlisTestResultRow> results) {
        List<YunlisTestResultRow> accepted = new ArrayList<>();
        for (YunlisTestResultRow row : results) {
            String testId = trimToNull(row.getTestId());
            if (testId == null) {
                continue;
            }
            LabItemResolveModel resolved = codeMapQueryService.resolveLabItem(tenantId, testId);
            if (resolved == null || !Boolean.TRUE.equals(resolved.getFound())) {
                continue;
            }
            if (!Boolean.TRUE.equals(resolved.getPushEnabled())) {
                continue;
            }
            accepted.add(row);
        }
        return accepted;
    }

    private static boolean isConsumableReceiveFlag(String receiveFlag) {
        String flag = trimToNull(receiveFlag);
        if (RECEIVE_FLAG_AUDITED.equals(flag)) {
            return true;
        }
        return RECEIVE_FLAG_PRINTED.equals(flag);
    }

    private static Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return Instant.now();
        }
        return time.atZone(ZONE).toInstant();
    }

    private static String firstNonBlank(String first, String second) {
        String a = trimToNull(first);
        if (a != null) {
            return a;
        }
        return trimToNull(second);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= OUTBOX_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, OUTBOX_MESSAGE_MAX_LENGTH);
    }
}
