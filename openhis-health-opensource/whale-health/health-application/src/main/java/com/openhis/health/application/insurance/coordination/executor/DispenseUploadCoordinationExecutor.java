/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.coordination.dto.DispenseUploadCoordinationSnapshot;
import com.openhis.health.domain.insurance.coordination.command.AppendYbCoordinationStepCommand;
import com.openhis.health.domain.insurance.coordination.command.TransitionYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.chs.jilin.port.InventoryInsurancePort;
import com.openhis.whale.chs.jilin.port.model.UploadAfterDispenseCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterReturnCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/** 发药/退药后进销存协同单步骤执行器（DISPENSE_UPLOAD：3505/3506，non-blocking）。 */
@Slf4j
@Component
@ConditionalOnBean(InventoryInsurancePort.class)
@RequiredArgsConstructor
public class DispenseUploadCoordinationExecutor implements YbCoordinationStepExecutor {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationStepRepository stepRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private InventoryInsurancePort inventoryInsurancePort;

    /**
     * 本执行器处理的协同单业务类型：发药/退药进销存上传。
     *
     * @return {@link YbCoordinationBizType#DISPENSE_UPLOAD}
     */
    @Override
    public YbCoordinationBizType bizType() {
        return YbCoordinationBizType.DISPENSE_UPLOAD;
    }

    /**
     * 推进 DISPENSE_UPLOAD 协同单：CREATED 态时执行进销存上传阶段。
     *
     * @param coordination 协同单聚合；为 null 或非 CREATED 态时静默返回
     */
    @Override
    public void dispatch(YbCoordination coordination) {
        if (coordination == null || coordination.getId() == null) {
            return;
        }
        // Gate：仅 CREATED 态可进入上传阶段，避免重复调用平台。
        YbCoordinationState state = coordination.stateEnum();
        if (!YbCoordinationState.CREATED.equals(state)) {
            return;
        }
        // Cmd：执行 3505/3506 上传并推进步骤状态。
        this.runUploadPhase(coordination);
    }

    private void runUploadPhase(YbCoordination coordination) {
        this.transition(coordination.getId(), YbCoordinationState.PLATFORM_RUNNING, null, null);
        DispenseUploadCoordinationSnapshot snapshot = this.readSnapshot(coordination.getRequestSnapshot());
        if (inventoryInsurancePort == null || snapshot == null) {
            this.failUpload(coordination, "医保进销存 Port 未启用或协同单上下文缺失");
            return;
        }
        List<Long> dispenseIds = snapshot.getDispenseIds();
        if (snapshot.getEncounterId() == null || dispenseIds == null || dispenseIds.isEmpty()) {
            this.failUpload(coordination, "缺少 encounterId 或 dispenseIds");
            return;
        }
        String infno = InsuranceCoordinationConstants.DISPENSE_UPLOAD_SALE_INFNO;
        try {
            if (InsuranceCoordinationConstants.DISPENSE_UPLOAD_KIND_RETURN.equals(snapshot.getUploadKind())) {
                infno = InsuranceCoordinationConstants.DISPENSE_UPLOAD_RETURN_INFNO;
                UploadAfterReturnCommand command = new UploadAfterReturnCommand()
                    .setEncounterId(snapshot.getEncounterId())
                    .setDispenseIds(dispenseIds);
                inventoryInsurancePort.uploadAfterOutpatientReturn(command);
            } else {
                UploadAfterDispenseCommand command = new UploadAfterDispenseCommand()
                    .setEncounterId(snapshot.getEncounterId())
                    .setDispenseIds(dispenseIds);
                inventoryInsurancePort.uploadAfterDispense(command);
            }
            int stepOrder = stepRepository.countByCoordinationIdAndIsDeletedFalse(coordination.getId()) + 1;
            AppendYbCoordinationStepCommand stepCmd = new AppendYbCoordinationStepCommand();
            stepCmd.setCoordinationId(coordination.getId());
            stepCmd.setStepOrder(stepOrder);
            stepCmd.setInfno(infno);
            stepCmd.setMsgid(coordination.getMsgid());
            stepCmd.setState(YbCoordinationState.COMPLETED.getCode());
            coordinationDomainService.appendStep(stepCmd);
            this.transition(coordination.getId(), YbCoordinationState.COMPLETED, null, null);
        } catch (RuntimeException ex) {
            log.warn("DISPENSE_UPLOAD 协同单上传失败 coordinationId={}", coordination.getId(), ex);
            this.failUpload(coordination, ex.getMessage());
        }
    }

    private void failUpload(YbCoordination coordination, String message) {
        this.transition(coordination.getId(), YbCoordinationState.FAILED, InsuranceCoordinationConstants.INS_012, message);
    }

    private void transition(Long coordinationId, YbCoordinationState target, String errorCode, String errorMsg) {
        TransitionYbCoordinationCommand cmd = new TransitionYbCoordinationCommand();
        cmd.setCoordinationId(coordinationId);
        cmd.setTargetState(target);
        cmd.setErrorCode(errorCode);
        cmd.setErrorMsg(errorMsg);
        coordinationDomainService.transition(cmd);
    }

    private DispenseUploadCoordinationSnapshot readSnapshot(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DispenseUploadCoordinationSnapshot.class);
        } catch (Exception ex) {
            log.warn("DISPENSE_UPLOAD 快照解析失败", ex);
            return null;
        }
    }
}
