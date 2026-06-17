/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.config.InsuranceCoordinationProperties;
import com.openhis.health.application.insurance.coordination.dto.DispenseUploadCoordinationSnapshot;
import com.openhis.health.application.insurance.coordination.worker.YbCoordinationWorker;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.insurance.coordination.command.CreateYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.InsuranceSchemeCode;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/** 发药/退药后进销存异步协同单编排（DISPENSE_UPLOAD）。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = "dispenseUploadCoordinationExecutor")
public class MedicareInventoryUploadCoordinator {

    private final YbCoordinationDomainService coordinationDomainService;
    private final EncounterQueryService encounterQueryService;
    private final ContractQueryService contractQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ObjectMapper objectMapper;
    private final InsuranceCoordinationProperties coordinationProperties;

    @Autowired(required = false)
    private YbCoordinationWorker coordinationWorker;

    /**
     * 创建 DISPENSE_UPLOAD 协同单并异步调度；失败仅记录日志，不影响本地发药/退药。
     */
    public void scheduleUploadIfEnabled(
        Long encounterId,
        List<Long> dispenseIds,
        Long operatorId,
        String uploadKind
    ) {
        if (!coordinationProperties.isDispenseUploadAsyncEnabled()) {
            return;
        }
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        try {
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
            Long patientId = encounter.getPatientId();
            if (patientId == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "就诊缺少患者");
            }
            Contract contract = resolveContract(encounterId);
            DispenseUploadCoordinationSnapshot snapshot = new DispenseUploadCoordinationSnapshot();
            snapshot.setEncounterId(encounterId);
            snapshot.setDispenseIds(dispenseIds);
            snapshot.setUploadKind(uploadKind);
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            CreateYbCoordinationCommand createCmd = new CreateYbCoordinationCommand();
            createCmd.setBizType(YbCoordinationBizType.DISPENSE_UPLOAD);
            createCmd.setSchemeCode(InsuranceSchemeCode.CHS_NATIONAL_JILIN);
            createCmd.setBizId(encounterId);
            createCmd.setContractId(contract.getId());
            createCmd.setEncounterId(encounterId);
            createCmd.setPatientId(patientId);
            createCmd.setOperatorId(operatorId);
            createCmd.setPriority(YbCoordinationPriority.P1);
            createCmd.setBlocking(false);
            createCmd.setRequestSnapshot(snapshotJson);
            createCmd.setMsgid(buildMsgid(encounterId, uploadKind));
            YbCoordination coordination = coordinationDomainService.create(createCmd);
            if (coordinationWorker != null && coordination.getId() != null) {
                coordinationWorker.dispatchAsync(coordination.getId());
            }
        } catch (Exception ex) {
            log.warn(
                "创建 DISPENSE_UPLOAD 协同单失败 encounterId={} uploadKind={}",
                encounterId,
                uploadKind,
                ex);
        }
    }

    private Contract resolveContract(Long encounterId) {
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && StringUtils.isNotBlank(payment.getContractNo())) {
                return contractQueryService.findOptionalContractEntityByBusNo(payment.getContractNo().trim())
                    .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.CONTRACT_NOT_FOUND, "医保契约不存在: " + payment.getContractNo()));
            }
        }
        throw new BusinessException(ErrorCodeConstants.CONTRACT_NOT_FOUND, "无法解析医保契约，请先完成收费");
    }

    private static String buildMsgid(Long encounterId, String uploadKind) {
        String kind = StringUtils.defaultIfBlank(uploadKind, InsuranceCoordinationConstants.DISPENSE_UPLOAD_KIND_SALE);
        return "DISP-UP-" + encounterId + "-" + kind + "-" + System.currentTimeMillis();
    }
}
