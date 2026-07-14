/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.observability;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.invoke.command.AppendYbInvokeLogCommand;
import com.openhis.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.openhis.chs.gateway.client.ChsFileUploadResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.support.YbInvokeLogAuditSupport;

/**
 * {@link InsuranceGatewayClient} 装饰器：每次平台调用后写入 {@code yb_invoke_log} 全报文审计。
 *
 * <p>落库经 {@link YbInvokeLogDomainService#append}，不在 gateway 直连 Repository。<。</p> */
public class PersistingInsuranceGatewayClient implements InsuranceGatewayClient {

    private final InsuranceGatewayClient delegate;
    private final YbInvokeLogDomainService invokeLogDomainService;

    /**
     * @param delegate 真实 HTTP 客户端
     * @param invokeLogDomainService 审计落库；{@code null} 时退化为纯委托 */
    public PersistingInsuranceGatewayClient(
        InsuranceGatewayClient delegate,
        YbInvokeLogDomainService invokeLogDomainService) {
        this.delegate = delegate;
        this.invokeLogDomainService = invokeLogDomainService;
    }

    /**
     * 调用国家平台 infno 交易并落审计日志。
     *
     * @param contractBusNo 契约 bus_no
     * @param infNo 交易号（见 {@link ChsInfNo}）
     * @param input 报文 input 节点
     * @param context 操作员、参保地等路由字段
     * @return 平台响应体 */
    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        long startNanos = System.nanoTime();
        String requestJson = YbInvokeLogAuditSupport.serializeRequest(input);
        ResultBody body = null;
        RuntimeException invokeError = null;
        try {
            body = delegate.invoke(contractBusNo, infNo, input, context);
            return body;
        } catch (RuntimeException ex) {
            invokeError = ex;
            throw ex;
        } finally {
            writeLog(contractBusNo, infNo, requestJson, body, invokeError, startNanos);
        }
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return delegate.signIn(contractBusNo, context);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        return delegate.downloadFile(contractBusNo, input, context);
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        return delegate.uploadFile(contractBusNo, zipBytes, filename, context);
    }

    private void writeLog(
        String contractBusNo,
        String infNo,
        String requestJson,
        ResultBody body,
        RuntimeException invokeError,
        long startNanos) {
        if (invokeLogDomainService == null) {
            return;
        }
        ChsInvokeLogContext ctx = ChsInvokeLogContextHolder.get();
        AppendYbInvokeLogCommand command = new AppendYbInvokeLogCommand()
            .setContractBusNo(contractBusNo)
            .setInfno(infNo)
            .setInfname(YbInvokeLogAuditSupport.resolveInfname(infNo, null))
            .setRequestJson(requestJson)
            .setInfcode(YbInvokeLogAuditSupport.resolveInfcode(body, invokeError))
            .setErrMsg(YbInvokeLogAuditSupport.resolveErrMsg(body, invokeError))
            .setDurationMs((int) ((System.nanoTime() - startNanos) / 1_000_000L));
        if (body != null) {
            command.setResponseJson(YbInvokeLogAuditSupport.serializeResponseBody(body));
        } else if (invokeError != null) {
            command.setResponseJson(YbInvokeLogAuditSupport.serializeInvokeException(invokeError));
        }
        if (ctx != null) {
            command.setContractId(ctx.getContractId())
                .setEncounterId(ctx.getEncounterId())
                .setPaymentDraftId(ctx.getPaymentDraftId())
                .setChrgBchno(ctx.getChrgBchno())
                .setPreSettleId(ctx.getPreSettleId())
                .setSettlementRecordId(ctx.getSettlementRecordId())
                .setTenantId(ctx.getTenantId());
            if (isNotEmpty(ctx.getMsgid())) {
                command.setMsgid(ctx.getMsgid().trim());
            }
        }
        Long invokeLogId = invokeLogDomainService.append(command);
        if (invokeLogId != null) {
            ChsInvokeLogContextHolder.merge(c -> c.setLastInvokeLogId(invokeLogId));
        }
    }
}
