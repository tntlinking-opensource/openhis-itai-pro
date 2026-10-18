/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.bridge.settlement.support.ChsActivityPackageFeeExpandSupport;
import com.zxplat.plugin.chs.domain.inpatient.command.IpRevokeFeeDetailCommand;
import com.zxplat.plugin.chs.domain.inpatient.result.IpRevokeFeeDetailResult;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.zxplat.plugin.chs.domain.settlement.YbFeeUploadStatus;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2302Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 【2302】撤销住院费用明细 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_inpatient_fee_upload。
 * 2305 只把本地行标成 REVOKED、并不删除平台明细；本接口仍要带这些流水出站。
 * 套餐子项若历史只留下最后一条流水，按子账单补齐同父下的全部 {@code feedetl_sn}。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2302Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbInpatientFeeUploadQueryService feeUploadQueryService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;
    private final ChsActivityPackageFeeExpandSupport activityPackageFeeExpandSupport;

    /**
     * 撤销已上传住院费用明细并更新本地上传记录。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo 及收费项 ID 列表
     * @return 撤销结果（撤销条数）
     */
    public IpRevokeFeeDetailResult invoke(IpRevokeFeeDetailCommand command) {
        InpatientSettleCommandAssembler.validateEncounterPlatformCommand(
            command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        List<YbInpatientFeeUpload> uploads = resolveUploadedFees(command, tenantId);
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        return this.revokeUploadedRows(
            command.getContractBusNo(), command.getMdtrtId(), command.getPsnNo(),
            uploads, invokeContext);
    }

    /**
     * 按已解析的上传行直接出站 2302（预结算清扫孤儿明细时复用，避免再按收费项二次查询）。
     *
     * @param contractBusNo 契约业务号
     * @param mdtrtId       平台就诊号
     * @param psnNo         人员编号
     * @param uploads       待撤销的本地上传行（UPLOADED / SETTLED / 2305 已标的 REVOKED）
     * @param invokeContext 出站上下文
     * @return 撤销结果（撤销条数）
     */
    public IpRevokeFeeDetailResult revokeUploadedRows(
            String contractBusNo,
            String mdtrtId,
            String psnNo,
            List<YbInpatientFeeUpload> uploads,
            InvokeContext invokeContext) {
        if (uploads == null || uploads.isEmpty()) {
            return new IpRevokeFeeDetailResult().setRevokedCount(0);
        }
        List<String> feedetlSnList = this.collectFeedetlSns(uploads);
        // Gate：过滤后无有效流水则跳过平台调用
        if (feedetlSnList.isEmpty()) {
            return new IpRevokeFeeDetailResult().setRevokedCount(0);
        }
        this.invoke2302AllowingAbsent(contractBusNo, mdtrtId, psnNo, feedetlSnList, invokeContext);

        inpatientFeeUploadDomainService.markRevokedByUploads(uploads);
        return new IpRevokeFeeDetailResult().setRevokedCount(uploads.size());
    }

    /**
     * 按收费项 ID 解析仍可能留在平台上的费用明细。
     *
     * <p>2305 成功后本地已是 REVOKED，但平台明细还在；只认 UPLOADED 会让随后的 2302 空跑。</p>
     *
     * @param command 撤销命令（含 chargeItemIds）
     * @param tenantId 租户 ID
     * @return 待撤销的费用上传记录列表
     */
    private List<YbInpatientFeeUpload> resolveUploadedFees(IpRevokeFeeDetailCommand command, Long tenantId) {
        List<YbInpatientFeeUpload> uploads = new ArrayList<>();
        if (command.getChargeItemIds() == null || command.getChargeItemIds().isEmpty()) {
            return uploads;
        }
        for (Long chargeItemId : command.getChargeItemIds()) {
            if (chargeItemId == null) {
                continue;
            }
            for (YbInpatientFeeUpload row : feeUploadQueryService.listByChargeItemId(chargeItemId, tenantId)) {
                if (row == null) {
                    continue;
                }
                String status = row.getUploadStatus();
                if (YbFeeUploadStatus.UPLOADED.equals(status)
                    || YbFeeUploadStatus.SETTLED.equals(status)
                    || YbFeeUploadStatus.REVOKED.equals(status)) {
                    uploads.add(row);
                }
            }
        }
        return uploads;
    }

    /**
     * 汇总待撤销流水：本地行上的 {@code feedetl_sn}，再补套餐子账单曾上传、但被同一收费项覆盖掉的兄弟流水。
     *
     * @param uploads 本地上传行
     * @return 去重后的流水号
     */
    private List<String> collectFeedetlSns(List<YbInpatientFeeUpload> uploads) {
        LinkedHashSet<String> feedetlSnSet = new LinkedHashSet<>();
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (YbInpatientFeeUpload upload : uploads) {
            if (upload == null || upload.getFeedetlSn() == null || upload.getFeedetlSn().isBlank()) {
                continue;
            }
            feedetlSnSet.add(upload.getFeedetlSn().trim());
            if (upload.getChargeItemId() != null) {
                chargeItemIds.add(upload.getChargeItemId());
            }
        }
        for (Long chargeItemId : chargeItemIds) {
            for (String childSn : activityPackageFeeExpandSupport.listPersistedChildFeedetlSns(chargeItemId)) {
                if (childSn == null || childSn.isBlank()) {
                    continue;
                }
                feedetlSnSet.add(childSn.trim());
            }
        }
        return new ArrayList<>(feedetlSnSet);
    }

    /**
     * 出站 2302。整批因「流水已不在平台」失败时，改逐条撤销，已不存在的流水视为成功。
     *
     * @param contractBusNo 契约业务号
     * @param mdtrtId       平台就诊号
     * @param psnNo         人员编号
     * @param feedetlSnList 费用流水号
     * @param invokeContext 出站上下文
     */
    private void invoke2302AllowingAbsent(
            String contractBusNo,
            String mdtrtId,
            String psnNo,
            List<String> feedetlSnList,
            InvokeContext invokeContext) {
        try {
            this.invoke2302Once(contractBusNo, mdtrtId, psnNo, feedetlSnList, invokeContext);
        } catch (RuntimeException ex) {
            if (!isPlatformFeeAlreadyAbsent(ex)) {
                throw ex;
            }
            // 整批里夹了本地轧差、从未上传的流水时，平台会整单拒绝；逐条才能撤掉仍在平台上的行
            if (feedetlSnList.size() <= 1) {
                return;
            }
            for (String feedetlSn : feedetlSnList) {
                List<String> one = new ArrayList<>();
                one.add(feedetlSn);
                try {
                    this.invoke2302Once(contractBusNo, mdtrtId, psnNo, one, invokeContext);
                } catch (RuntimeException oneEx) {
                    if (!isPlatformFeeAlreadyAbsent(oneEx)) {
                        throw oneEx;
                    }
                }
            }
        }
    }

    /**
     * 单次 2302 出站。
     *
     * @param contractBusNo 契约业务号
     * @param mdtrtId       平台就诊号
     * @param psnNo         人员编号
     * @param feedetlSnList 费用流水号
     * @param invokeContext 出站上下文
     */
    private void invoke2302Once(
            String contractBusNo,
            String mdtrtId,
            String psnNo,
            List<String> feedetlSnList,
            InvokeContext invokeContext) {
        Ip2302Input input2302 = IpInpatientSettleInputAssembler.assemble2302(
            mdtrtId, psnNo, feedetlSnList);
        ResultBody body2302 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_FEE_REVOKE, input2302, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2302, ChsInfNo.INPATIENT_FEE_REVOKE);
    }

    /**
     * 平台是否表示该费用流水已经不在（重复 2302、本地轧差未上传）。
     *
     * @param ex 出站异常
     * @return 可视为已撤销时为 true
     */
    private static boolean isPlatformFeeAlreadyAbsent(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage();
        if (msg.contains("已撤销") || msg.contains("已经撤销") || msg.contains("重复撤销")) {
            return true;
        }
        if (msg.contains("未查询到") && (msg.contains("费用") || msg.contains("流水"))) {
            return true;
        }
        return msg.contains("不存在") && (msg.contains("费用") || msg.contains("明细") || msg.contains("流水"));
    }
}
