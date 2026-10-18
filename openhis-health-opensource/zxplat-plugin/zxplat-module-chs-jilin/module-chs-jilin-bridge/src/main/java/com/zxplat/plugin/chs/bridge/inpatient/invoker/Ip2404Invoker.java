/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2404Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2404】入院撤销 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：写 yb_encounter_platform_map（软删映射）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2404Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;

    /**
     * 撤销入院登记并软删本地平台映射。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo 及 operatorId
     */
    public void invoke(IpEncounterPlatformCommand command) {
        InpatientSettleCommandAssembler.validateEncounterPlatformCommand(
            command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        Ip2404Input input2404 = IpInpatientSettleInputAssembler.assemble2404Or2405(
            command.getMdtrtId(), command.getPsnNo());
        // Cmd：出站前写入就诊，取消登记产生的 2404 才能在日志里按就诊号和患者查到
        ResultBody body2404 = this.invokeRevoke(command, input2404, invokeContext);
        // 平台已无在院记录时仍软删本地映射，避免转自费重试后预结算继续按死号上传
        if (!this.isPlatformSuccess(body2404)) {
            if (this.isAdmissionAlreadyRevoked(body2404)) {
                this.softDeleteLocalMap(command);
                return;
            }
        }
        ChsPlatformResponseSupport.requireSuccess(body2404, ChsInfNo.INPATIENT_ADMISSION_REVOKE);
        this.softDeleteLocalMap(command);
    }

    /**
     * 平台交易是否成功。
     *
     * @param body 平台响应
     * @return infcode 为成功码时 true
     */
    private boolean isPlatformSuccess(ResultBody body) {
        // 无响应不能当成成功，交给后续失败处理
        if (body == null || body.getInfcode() == null) {
            return false;
        }
        return ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
    }

    /**
     * 平台是否表示本次入院已经不在院（重复 2404 可视为已撤销）。
     *
     * @param body 平台响应
     * @return 文案表明入院已撤销或不存在时 true
     */
    private boolean isAdmissionAlreadyRevoked(ResultBody body) {
        // 没有错误文案时不能猜测成已撤销
        if (body == null || body.getErrMsg() == null) {
            return false;
        }
        String message = body.getErrMsg();
        // 重复撤销：本地映射仍应软删
        if (message.contains("已撤销") || message.contains("已经撤销")) {
            return true;
        }
        // 平台已无该就诊
        if (message.contains("未查询到") && message.contains("就诊")) {
            return true;
        }
        if (message.contains("不存在") && (message.contains("入院") || message.contains("就诊"))) {
            return true;
        }
        return false;
    }

    /**
     * 软删本地平台入院映射，与 2404 成功后的副作用一致。
     *
     * @param command 入院撤销命令
     */
    private void softDeleteLocalMap(IpEncounterPlatformCommand command) {
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        encounterPlatformMapDomainService.softDeleteEncounterMap(
            command.getEncounterId(),
            command.getContractId(),
            command.getMdtrtId(),
            command.getMedType(),
            command.getOperatorId(),
            tenantId);
    }

    /**
     * 出站 2404，并把就诊、契约、租户写入调用日志。
     *
     * <p>取消登记入口不另设日志上下文。调用方已持有上下文时只合并字段，出站后不清理。</p>
     *
     * @param command       入院撤销命令
     * @param input2404     2404 报文
     * @param invokeContext 操作员与签到上下文
     * @return 平台响应体
     */
    private ResultBody invokeRevoke(
            IpEncounterPlatformCommand command,
            Ip2404Input input2404,
            InvokeContext invokeContext) {
        // 取消登记通常没有外层日志上下文；已有上下文的链路不能在这里清理
        boolean createdHere = ChsInvokeLogContextHolder.get() == null;
        ChsInvokeLogContextHolder.merge(ctx -> {
            // 有就诊主键才写入，空值不覆盖外层已挂上的就诊
            if (command.getEncounterId() != null) {
                ctx.setEncounterId(command.getEncounterId());
            }
            // 有契约才写入，供日志按契约筛选
            if (command.getContractId() != null) {
                ctx.setContractId(command.getContractId());
            }
            // 有租户才写入，避免空租户覆盖外层
            if (command.getTenantId() != null) {
                ctx.setTenantId(command.getTenantId());
            }
        });
        try {
            return gatewayClient.invoke(
                command.getContractBusNo(), ChsInfNo.INPATIENT_ADMISSION_REVOKE, input2404, invokeContext);
        } finally {
            // 仅释放本次新建的上下文，防止线程复用把上一位患者的就诊号带到下一次调用
            if (createdHere) {
                ChsInvokeLogContextHolder.clear();
            }
        }
    }
}
