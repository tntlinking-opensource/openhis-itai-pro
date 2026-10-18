/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.plugin.einvoice.boss.domain.share.BossServiceIds;
import com.zxplat.plugin.einvoice.boss.domain.enums.WriteOffStatus;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.zxplat.plugin.einvoice.boss.domain.invoke.entity.EinvBossInvokeLog;
import com.zxplat.plugin.einvoice.boss.domain.invoke.repository.EinvBossInvokeLogRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 冲红状态展示解析：优先 document 字段，作废票可回退 invoke 日志并 backfill。
 */
@Component
@RequiredArgsConstructor
public class BossWriteOffStatusSupport {

    private final EinvBossInvokeLogRepository invokeLogRepository;
    private final EinvBossDocumentRepository documentRepository;

    /**
     * 解析冲红展示信息。
     *
     * @param document 博思单据
     * @param invoiceStatus health 票据状态（DRAFT/ISSUED/CANCELLED）
     * @return 冲红展示
     */
    public WriteOffDisplay resolve(EinvBossDocument document, String invoiceStatus) {
        WriteOffDisplay display = new WriteOffDisplay();
        if (document == null) {
            return display;
        }
        if (StringUtils.hasText(document.getWriteOffStatus())) {
            fill(display, document.getWriteOffStatus(), document.getWriteOffBillNo(),
                    document.getWriteOffAt(), document.getWriteOffMessage());
            return display;
        }
        if (!"CANCELLED".equals(invoiceStatus)) {
            return display;
        }
        if (!StringUtils.hasText(document.getBossBillNo())) {
            fill(display, WriteOffStatus.SKIPPED.getCode(), null, null, "未开出蓝票");
            return display;
        }
        Optional<EinvBossInvokeLog> invokeLog =
                invokeLogRepository.findFirstByBusNoAndServiceIdOrderByCreatedAtDesc(
                        document.getBusNo(), BossServiceIds.WRITE_OFF_E_BILL);
        invokeLog.ifPresent(log -> {
            if ("S0000".equals(log.getResultCode())) {
                fill(display, WriteOffStatus.SUCCESS.getCode(), null, log.getCreatedAt(), null);
                backfillFromInvokeLog(document, WriteOffStatus.SUCCESS.getCode(), log.getCreatedAt(), null);
            } else {
                fill(display, WriteOffStatus.FAILED.getCode(), null, log.getCreatedAt(), log.getResultCode());
                backfillFromInvokeLog(document, WriteOffStatus.FAILED.getCode(), log.getCreatedAt(), log.getResultCode());
            }
        });
        return display;
    }

    /**
     * 冲红状态码 → 列表/详情页中文标签。
     *
     * @param status SUCCESS/FAILED/SKIPPED 等
     * @return 展示文案；未知码原样返回
     */
    public String resolveLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        return switch (status) {
            case "SUCCESS" -> "冲红成功";
            case "FAILED" -> "冲红失败";
            case "SKIPPED" -> "无需冲红";
            default -> status;
        };
    }

    /**
     * 从 invoke 日志回填 document 冲红列（仅当 document 尚无 writeOffStatus 时）。
     *
     * @param document 单据
     * @param status 推断的冲红状态
     * @param at 日志时间
     * @param message 失败时的结果码摘要
     */
    private void backfillFromInvokeLog(
            EinvBossDocument document, String status, LocalDateTime at, String message) {
        if (StringUtils.hasText(document.getWriteOffStatus())) {
            return;
        }
        document.setWriteOffStatus(status);
        document.setWriteOffAt(at);
        if (StringUtils.hasText(message)) {
            document.setWriteOffMessage(message);
        }
        documentRepository.save(document);
    }

    /** 填充 {@link WriteOffDisplay} 各展示字段。 */
    private void fill(WriteOffDisplay display, String status, String billNo, LocalDateTime at, String message) {
        display.setWriteOffStatus(status);
        display.setWriteOffStatusLabel(resolveLabel(status));
        display.setWriteOffBillNo(billNo);
        display.setWriteOffAt(at);
        display.setWriteOffMessage(message);
    }

    /** 列表/详情页冲红展示字段。 */
    @Data
    public static class WriteOffDisplay {
        private String writeOffStatus;
        private String writeOffStatusLabel;
        private String writeOffBillNo;
        private LocalDateTime writeOffAt;
        private String writeOffMessage;
    }
}
