/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.audit.support;

import com.zxplat.plugin.chs.domain.audit.command.AuditAnalyze3101Command;
import com.zxplat.plugin.chs.domain.audit.command.AuditEncounterCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditFeedback3103Command;
import com.zxplat.plugin.chs.domain.audit.command.AuditFeedbackWarnCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditPatientCommand;
import com.zxplat.plugin.chs.domain.audit.config.ChsAuditProperties;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsDetailAuditConstants;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.session.ChsSignCredentialResolver;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 明细审核（3101/3103）出站公共支撑：契约解析、签到上下文、Gate 校验、syscode 补全。
 */
@Component
@RequiredArgsConstructor
public class AuditInvokeSupport {

    private final ChsInsuranceContractReader contractReader;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;
    private final ChsAuditProperties auditProperties;

    /**
     * 解析契约 bus_no。
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @return bus_no
     */
    public String resolveBusNo(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        return contractReader.requireContractBusNo(contract);
    }

    /**
     * 构建含签到凭证的出站上下文。
     *
     * @return InvokeContext
     */
    public InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再调用明细审核");
        InvokeContext partial = InvokeContext.builder().operatorId(String.valueOf(operatorId)).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /**
     * 当前操作员 ID（落库用）。
     *
     * @return operatorId
     */
    public Long requireOperatorId() {
        return currentOperatorResolver.requireOperatorId("请先登录后再调用明细审核");
    }

    /**
     * 补全并校验 3101 命令。
     *
     * @param command 入参（可被改写 syscode）
     */
    public void prepareAnalyze3101(AuditAnalyze3101Command command) {
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "3101 命令不能为空");
        }
        // Gate：syscode 必填（入参覆盖配置，无默认）
        if (StringUtils.isEmpty(command.getSyscode())) {
            command.setSyscode(auditProperties.getSyscode());
        }
        if (StringUtils.isEmpty(command.getSyscode())) {
            throw new BusinessException(
                ChsErrorCodes.AUDIT_SYSCODE_REQUIRED,
                "未配置 zxplat.chs.audit.syscode，且命令未传 syscode");
        }
        boolean hasTrig = StringUtils.isNotEmpty(command.getTrigScen());
        boolean hasRules = command.getRuleIds() != null && !command.getRuleIds().isEmpty();
        if (!hasTrig && !hasRules) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "trigScen 与 ruleIds 至少填一项");
        }
        if (command.getPatients() == null || command.getPatients().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "patient_dtos 不能为空");
        }
        for (AuditPatientCommand patient : command.getPatients()) {
            if (patient == null || StringUtils.isEmpty(patient.getPatnId())) {
                throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "patn_id 不能为空");
            }
            if (patient.getEncounters() == null || patient.getEncounters().isEmpty()) {
                throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "fsi_encounter_dtos 不能为空");
            }
            for (AuditEncounterCommand encounter : patient.getEncounters()) {
                if (encounter == null || StringUtils.isEmpty(encounter.getMdtrtId())) {
                    throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "mdtrt_id 不能为空");
                }
                if (StringUtils.isEmpty(encounter.getMedinsAdmdvs())) {
                    throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "medins_admdvs 不能为空");
                }
            }
        }
    }

    /**
     * 校验 3103 命令。
     *
     * @param command 反馈命令
     */
    public void validateFeedback3103(AuditFeedback3103Command command) {
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "3103 命令不能为空");
        }
        if (StringUtils.isEmpty(command.getWarnType())) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "warnType 不能为空");
        }
        if (command.getWarns() == null || command.getWarns().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "warns 不能为空");
        }
        for (AuditFeedbackWarnCommand warn : command.getWarns()) {
            if (warn == null || StringUtils.isEmpty(warn.getWarnRsltId())) {
                throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "warn_rslt_id 不能为空");
            }
            if (StringUtils.isEmpty(warn.getDspoWay())) {
                throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "dspo_way 不能为空");
            }
            // Gate：继续执行时处置原因必填
            if (ChsDetailAuditConstants.DSPO_CONTINUE.equals(warn.getDspoWay())
                    && StringUtils.isEmpty(warn.getDspoWayRea())) {
                throw new BusinessException(
                    ChsErrorCodes.AUDIT_PARAM_INVALID,
                    "dspo_way=1（继续执行）时 dspo_way_rea 必填");
            }
        }
    }
}
