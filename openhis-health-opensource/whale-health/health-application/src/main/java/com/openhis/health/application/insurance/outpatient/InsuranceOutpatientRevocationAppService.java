/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.outpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.coordination.dto.OutpatientOpSettleReverseCoordinationSnapshot;
import com.openhis.health.application.insurance.coordination.worker.YbCoordinationWorker;
import com.openhis.health.application.insurance.outpatient.request.GetOpInsuranceRevokeStatusRequest;
import com.openhis.health.application.insurance.outpatient.response.GetOpInsuranceRevokeStatusResponse;
import com.openhis.health.domain.insurance.coordination.command.CreateYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationQueryService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.InsuranceSchemeCode;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.chs.jilin.port.OutpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceSettleRecordResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 门诊医保结算撤销协同单门面（OP_SETTLE_REVERSE / 2208，06-协同单与 Worker §3）。
 *
 * <p>职责边界：查询原单医保结算记录；无记录时同步返回已撤销；有记录时创建 OP_SETTLE_REVERSE 协同单并异步派发 Worker。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = "opSettleReverseCoordinationExecutor")
public class InsuranceOutpatientRevocationAppService {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationQueryService coordinationQueryService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OutpatientInsuranceSettlementPort outpatientInsuranceSettlementPort;

    @Autowired(required = false)
    private YbCoordinationWorker coordinationWorker;

    /**
     * 启动门诊医保结算撤销协同单；原单无医保结算记录时直接返回同步已撤销。
     *
     * @param command 含原支付单、就诊、患者与操作员上下文
     * @return 协同单 id 或同步 NO_OP 结果
     */
    @Transactional
    public StartOpInsuranceRevokeResult startOutpatientInsuranceRevoke(StartOpInsuranceRevokeCommand command) {
        // Gate：医保 Port 未启用时无法发起平台撤销
        if (outpatientInsuranceSettlementPort == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "医保 Port 未启用");
        }
        // Pre：查询原支付单下全部医保结算记录
        List<OpInsuranceSettleRecordResult> records =
            outpatientInsuranceSettlementPort.listSettlementsByPayment(command.getOriginalPaymentId());
        // Gate：无医保结算记录时无需创建协同单，同步返回已撤销
        if (records.isEmpty()) {
            StartOpInsuranceRevokeResult empty = new StartOpInsuranceRevokeResult();
            empty.setOriginalPaymentId(command.getOriginalPaymentId());
            empty.setSettlementMode(InsuranceCoordinationConstants.SETTLEMENT_MODE_SYNC);
            empty.setInsuranceRevoked(true);
            return empty;
        }
        // Pre：命令未传合同时，从结算记录中解析合同 id
        Long contractId = command.getContractId();
        if (contractId == null) {
            for (OpInsuranceSettleRecordResult row : records) {
                if (row != null && row.getContractId() != null) {
                    contractId = row.getContractId();
                    break;
                }
            }
        }
        // Gate：无法解析合同时拒绝创建撤销协同单
        if (contractId == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "无法解析医保合同标识");
        }
        // Pre：组装撤销协同单请求快照
        OutpatientOpSettleReverseCoordinationSnapshot snapshot = new OutpatientOpSettleReverseCoordinationSnapshot();
        snapshot.setOriginalPaymentId(command.getOriginalPaymentId());
        snapshot.setEncounterId(command.getEncounterId());
        snapshot.setPatientId(command.getPatientId());
        snapshot.setOperatorId(command.getOperatorId());
        snapshot.setContractId(command.getContractId());
        snapshot.setAccountId(command.getAccountId());
        List<Long> recordIds = new ArrayList<>();
        // Loop：收集待撤销的医保结算记录 id
        for (OpInsuranceSettleRecordResult row : records) {
            if (row != null && row.getSettlementRecordId() != null) {
                recordIds.add(row.getSettlementRecordId());
            }
        }
        snapshot.setSettlementRecordIds(recordIds);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单上下文序列化失败");
        }
        // Cmd：创建 OP_SETTLE_REVERSE 协同单
        CreateYbCoordinationCommand createCmd = new CreateYbCoordinationCommand();
        createCmd.setBizType(YbCoordinationBizType.OP_SETTLE_REVERSE);
        createCmd.setSchemeCode(InsuranceSchemeCode.CHS_NATIONAL_JILIN);
        createCmd.setBizId(command.getOriginalPaymentId());
        createCmd.setAccountId(command.getAccountId());
        createCmd.setContractId(contractId);
        createCmd.setPaymentId(command.getOriginalPaymentId());
        createCmd.setPaymentNo(command.getPaymentNo());
        createCmd.setEncounterId(command.getEncounterId());
        createCmd.setPatientId(command.getPatientId());
        createCmd.setOperatorId(command.getOperatorId());
        createCmd.setPriority(YbCoordinationPriority.P0);
        createCmd.setBlocking(true);
        createCmd.setRequestSnapshot(snapshotJson);
        createCmd.setMsgid("OR" + command.getOriginalPaymentId() + System.currentTimeMillis());
        YbCoordination coordination = coordinationDomainService.create(createCmd);
        // Cmd：Worker 可用时异步派发平台 2208 撤销
        if (coordinationWorker != null) {
            coordinationWorker.dispatchAsync(coordination.getId());
        }
        // Asm：装配异步撤销启动响应
        StartOpInsuranceRevokeResult response = new StartOpInsuranceRevokeResult();
        response.setOriginalPaymentId(command.getOriginalPaymentId());
        response.setSettlementMode(InsuranceCoordinationConstants.SETTLEMENT_MODE_INSURANCE_ASYNC);
        response.setCoordinationIds(Collections.singletonList(coordination.getId()));
        response.setInsuranceRevoked(false);
        return response;
    }

    /**
     * 查询门诊医保结算撤销协同单进度。
     *
     * @param request 含原支付单主键
     * @return 协同单状态、医保结算号及是否已完成撤销
     */
    public GetOpInsuranceRevokeStatusResponse getOutpatientInsuranceRevokeStatus(
        GetOpInsuranceRevokeStatusRequest request) {
        // Pre：按原支付单加载协同单，筛选 OP_SETTLE_REVERSE 最新一条
        List<YbCoordination> coordinations = coordinationQueryService.listByPaymentId(request.getOriginalPaymentId());
        YbCoordination latest = null;
        // Loop：遍历协同单列表，取首个 OP_SETTLE_REVERSE 业务类型记录
        for (YbCoordination row : coordinations) {
            if (row != null && YbCoordinationBizType.OP_SETTLE_REVERSE.getCode().equals(row.getBizType())) {
                latest = row;
                break;
            }
        }
        // Gate：未找到撤销协同单时无法查询进度
        if (latest == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "未找到门诊撤销协同单");
        }
        // Asm：装配撤销状态响应
        GetOpInsuranceRevokeStatusResponse response = new GetOpInsuranceRevokeStatusResponse();
        response.setCoordinationId(latest.getId());
        response.setOriginalPaymentId(request.getOriginalPaymentId());
        response.setState(latest.getState());
        response.setYbSetlId(latest.getPlatformSetlId());
        response.setErrorMsg(latest.getErrorMsg());
        response.setCompleted(YbCoordinationState.COMPLETED.getCode().equals(latest.getState()));
        // Asm：协同单完成或 NO_OP 快照均视为医保已撤销
        response.setInsuranceRevoked(
            response.getCompleted() || "NO_OP".equals(latest.getResponseSnapshot()));
        return response;
    }

    /** 启动门诊医保撤销协同单入参。 */
    @Data
    @Accessors(chain = true)
    public static class StartOpInsuranceRevokeCommand {
        /** 原支付单主键。 */
        private Long originalPaymentId;
        /** 原支付单业务号。 */
        private String paymentNo;
        /** 医保合同主键（可空，由结算记录解析）。 */
        private Long contractId;
        /** 医保账户主键。 */
        private Long accountId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 操作员主键。 */
        private Long operatorId;
    }

    /** 启动门诊医保撤销协同单出参。 */
    @Data
    @Accessors(chain = true)
    public static class StartOpInsuranceRevokeResult {
        /** 原支付单主键。 */
        private Long originalPaymentId;
        /** 结算模式（SYNC 或 INSURANCE_ASYNC）。 */
        private String settlementMode;
        /** 创建的协同单 id 列表（同步 NO_OP 时为空）。 */
        private List<Long> coordinationIds;
        /** 是否已完成医保撤销（同步 NO_OP 时为 true）。 */
        private Boolean insuranceRevoked;
    }
}
