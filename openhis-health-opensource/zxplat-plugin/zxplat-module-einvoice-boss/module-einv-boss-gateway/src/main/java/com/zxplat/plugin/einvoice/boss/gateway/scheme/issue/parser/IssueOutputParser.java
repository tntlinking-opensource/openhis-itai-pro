/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.parser;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 开具/查票/冲红共用的票面结果解析器（gateway scheme 层）。
 *
 * <p>输入为 bridge Invoker 已解码的 {@link BossInvokeResult}，输出为 domain
 * {@link BossInvoiceIssueResult}。HTTP/base64 解码由 {@code BossResponseParser} 完成。</p>
 *
 * <p>冲红成功时红票专用字段 {@code eScarletBillNo/BatchCode} 保留在 {@link BossInvokeResult}，
 * 由 {@code BossWriteOffService#markSuccess} 写入 document 冲红列，本类仅映射通用蓝票字段。</p>
 */
@Component
public class IssueOutputParser {

    /**
     * 将博思成功响应映射为领域开具结果。
     *
     * @param invokeResult bridge 解析后的调用结果
     * @param busNo 业务流水号（调用方上下文，博思响应可能不含）
     * @param totalAmt 开票总额（查票场景 document 可能为空时由调用方传入）
     * @return 票面 DTO
     * @throws BusinessException {@code invokeResult.isSuccess()==false} 时抛出 {@link EinvBossErrorCodes#EINV_BOSS_REJECT}
     */
    public BossInvoiceIssueResult toIssueResult(BossInvokeResult invokeResult, String busNo, BigDecimal totalAmt) {
        if (!invokeResult.isSuccess()) {
            String msg = invokeResult.getRawMessage();
            throw new BusinessException(
                    EinvBossErrorCodes.EINV_BOSS_REJECT, "博思拒收: " + invokeResult.getResultCode() + " " + msg);
        }
        BossInvoiceIssueResult dto = new BossInvoiceIssueResult();
        dto.setBusNo(busNo);
        dto.setTotalAmt(totalAmt);
        dto.setBillNo(invokeResult.getBillNo());
        dto.setBatchCode(invokeResult.getBillBatchCode());
        dto.setRandom(invokeResult.getRandom());
        dto.setBillQrCode(invokeResult.getBillQrCode());
        dto.setPictureUrl(invokeResult.getPictureUrl());
        dto.setPictureNetUrl(invokeResult.getPictureNetUrl());
        dto.setBillCreateTime(invokeResult.getBillCreateTime());
        return dto;
    }

    /**
     * 从本地 boss 单据实体还原票面结果（幂等短路、已开票任务完成时使用）。
     *
     * @param document 已持久化蓝票信息的 {@link EinvBossDocument}
     * @return 与 document 字段对齐的 {@link BossInvoiceIssueResult}
     */
    public BossInvoiceIssueResult fromDocument(EinvBossDocument document) {
        BossInvoiceIssueResult result = new BossInvoiceIssueResult();
        result.setBusNo(document.getBusNo());
        result.setBillNo(document.getBossBillNo());
        result.setBatchCode(document.getBossBatchCode());
        result.setRandom(document.getRandom());
        result.setBillQrCode(document.getBillQrCode());
        result.setPictureUrl(document.getPictureUrl());
        result.setPictureNetUrl(document.getPictureNetUrl());
        result.setTotalAmt(document.getTotalAmt());
        return result;
    }

    /**
     * 将开具结果写回 boss 单据实体（不含冲红列、任务状态）。
     *
     * @param document 待更新的单据
     * @param result 博思票面结果
     * @param payloadJson 本次出站请求 JSON；查票补偿等无请求存档场景传 {@code null}
     */
    public void applyToDocument(EinvBossDocument document, BossInvoiceIssueResult result, String payloadJson) {
        document.setBossBillNo(result.getBillNo());
        document.setBossBatchCode(result.getBatchCode());
        document.setRandom(result.getRandom());
        document.setBillQrCode(result.getBillQrCode());
        document.setPictureUrl(result.getPictureUrl());
        document.setPictureNetUrl(result.getPictureNetUrl());
        if (payloadJson != null) {
            document.setPayloadJson(payloadJson);
        }
    }
}
