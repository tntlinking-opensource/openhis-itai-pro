/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.ChsFileUploadResult;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;

/**
 * {@link InsuranceGatewayClient} 装饰器：每次平台调用后写入 {@code yb_invoke_log} 全报文审计。
 *
 * <p>由 {@link InsuranceGatewayPersistenceBeanPostProcessor} 包装真实 HTTP 客户端 Bean。
 * 业务编排通过 {@link ChsInvokeLogContextHolder} 注入 encounterId/chrgBchno 等关联键，
 * 便于 2601 冲正按 msgid 追溯。</p>
 */
public class PersistingInsuranceGatewayClient implements InsuranceGatewayClient {

    private final InsuranceGatewayClient delegate;
    private final YbInvokeLogWriter invokeLogWriter;
    private final ChsInsuranceContractPort contractPort;

    /**
     * @param delegate 真实 HTTP 客户端（ChsHttpAccessor 等）
     * @param invokeLogWriter 审计落库；null 时退化为纯委托
     * @param contractPort 按 bus_no 解析 fin_contract.id；null 时仅 properties 兜底
     */
    public PersistingInsuranceGatewayClient(
        InsuranceGatewayClient delegate,
        YbInvokeLogWriter invokeLogWriter,
        ChsInsuranceContractPort contractPort) {
        this.delegate = delegate;
        this.invokeLogWriter = invokeLogWriter;
        this.contractPort = contractPort;
    }

    /**
     * 调用国家平台 infno 交易并落审计日志。
     *
     * @param contractBusNo 契约 bus_no（关联 chs-contract YAML）
     * @param infNo 交易号（见 {@link com.openhis.chs.api.ChsInfNo}）
     * @param input 报文 input 节点
     * @param context 操作员、参保地等路由字段
     * @return 平台响应体（含 infcode、output）
     */
    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        long startNanos = System.nanoTime();
        String requestJson = serialize(input);
        ResultBody body = null;
        try {
            // Cmd：委托真实客户端发 HTTP
            body = delegate.invoke(contractBusNo, infNo, input, context);
            return body;
        } finally {
            // Asm：无论成功/异常均写 yb_invoke_log（finally 保证超时也有审计）
            writeLog(contractBusNo, infNo, requestJson, body, startNanos);
        }
    }

    /**
     * 9001 签到（不经 invoke 包装，由 SignSessionAppService 单独落库）。
     */
    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return delegate.signIn(contractBusNo, context);
    }

    /** 9102 文件下载（二进制，不写 invoke 审计或另路处理）。 */
    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        return delegate.downloadFile(contractBusNo, input, context);
    }

    /** 9101 文件上传。 */
    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        return delegate.uploadFile(contractBusNo, zipBytes, filename, context);
    }

    /**
     * 组装审计命令并写入 yb_invoke_log；回填 lastInvokeLogId 供 Writer 关联 settlementRecord。
     */
    private void writeLog(
        String contractBusNo,
        String infNo,
        String requestJson,
        ResultBody body,
        long startNanos) {
        // 测试/无 Writer 时跳过
        if (invokeLogWriter == null) {
            return;
        }
        ChsInvokeLogContext ctx = ChsInvokeLogContextHolder.get();
        YbInvokeLogCommand command = new YbInvokeLogCommand()
            .setContractBusNo(contractBusNo)
            .setInfno(infNo)
            .setRequestJson(requestJson)
            .setInfcode(YbInvokeLogWriter.extractInfcode(body))
            .setDurationMs((int) ((System.nanoTime() - startNanos) / 1_000_000L));
        if (body != null) {
            command.setResponseJson(body.getOutput());
        }
        // 合并 ThreadLocal 中的业务关联键（由 AppService 在编排前 set）
        if (ctx != null) {
            command.setContractId(ctx.getContractId())
                .setEncounterId(ctx.getEncounterId())
                .setPaymentDraftId(ctx.getPaymentDraftId())
                .setChrgBchno(ctx.getChrgBchno())
                .setPreSettleId(ctx.getPreSettleId())
                .setSettlementRecordId(ctx.getSettlementRecordId())
                .setTenantId(ctx.getTenantId());
            if (org.apache.commons.lang3.StringUtils.isNotBlank(ctx.getMsgid())) {
                command.setMsgid(ctx.getMsgid().trim());
            }
        }
        if (command.getContractId() == null) {
            command.setContractId(resolveContractId(contractBusNo));
        }
        Long invokeLogId = invokeLogWriter.write(command);
        if (invokeLogId != null) {
            ChsInvokeLogContextHolder.merge(c -> c.setLastInvokeLogId(invokeLogId));
        }
    }

    /**
     * ThreadLocal 未带 contractId 时：优先 fin_contract（embedded），再 chs-contract.properties。
     * 解析失败不阻断 invoke 审计落库。
     */
    private Long resolveContractId(String contractBusNo) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return null;
        }
        if (contractPort != null) {
            try {
                ChsContractSnapshot snapshot = contractPort.requireContract(contractBusNo.trim(), null);
                if (snapshot != null && snapshot.id() != null) {
                    return snapshot.id();
                }
            } catch (RuntimeException ignored) {
                // 审计落库不因契约解析失败而中断
            }
        }
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config != null) {
            return config.getContractId();
        }
        return null;
    }

    private static String serialize(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String) {
            return (String) input;
        }
        return JSON.toJSONString(input);
    }
}
