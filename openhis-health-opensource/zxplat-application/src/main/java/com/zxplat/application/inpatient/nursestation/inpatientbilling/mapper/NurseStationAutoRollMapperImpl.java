/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.ActivateAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.CreateAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.DeleteAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.SuspendAutoGenRuleRequest;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.UpdateAutoGenRuleRequest;
import com.zxplat.domain.custom.chargeitemautogen.command.ActivateAutoGenRuleCommand;
import com.zxplat.domain.custom.chargeitemautogen.command.CreateAutoGenRuleCommand;
import com.zxplat.domain.custom.chargeitemautogen.command.DeleteAutoGenRuleCommand;
import com.zxplat.domain.custom.chargeitemautogen.command.SuspendAutoGenRuleCommand;
import com.zxplat.domain.custom.chargeitemautogen.command.UpdateAutoGenRuleCommand;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.servicerequest.command.EnsureAutoRollBedLongTermServiceRequestCommand;
import org.springframework.stereotype.Component;

/**
 * AS-IP-NUR-013：自动计费映射实现。
 */
@Component
public class NurseStationAutoRollMapperImpl implements NurseStationAutoRollMapper {

    /**
     * 新增配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CreateAutoGenRuleCommand toCreateAutoGenRuleCommand(CreateAutoGenRuleRequest request, Long operatorId) {
        CreateAutoGenRuleCommand command = new CreateAutoGenRuleCommand();
        command.setRuleName(request.getRuleName());
        command.setEncounterId(request.getEncounterId());
        // 自动计费配置固定 trigger 为诊疗活动表（BR-IP-ARG-003；不含耗材）
        command.setTriggerSourceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        command.setTriggerSourceId(request.getActivityId());
        command.setChargeDefinitionId(request.getChargeDefinitionId());
        command.setPerformingOrgId(request.getPerformingOrgId());
        command.setQuantityValue(request.getQuantityValue());
        command.setPriority(request.getPriority());
        command.setRemark(request.getRemark());
        command.setBusinessTime(request.getBusinessTime());
        command.setBillingPractitionerId(request.getBillingPractitionerId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 更新配置请求 → 领域命令（非空覆盖）。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public UpdateAutoGenRuleCommand toUpdateAutoGenRuleCommand(UpdateAutoGenRuleRequest request, Long operatorId) {
        UpdateAutoGenRuleCommand command = new UpdateAutoGenRuleCommand();
        command.setRuleId(request.getRuleId());
        command.setEncounterId(request.getEncounterId());
        command.setRuleName(request.getRuleName());
        if (request.getActivityId() != null) {
            command.setTriggerSourceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            command.setTriggerSourceId(request.getActivityId());
        }
        command.setChargeDefinitionId(request.getChargeDefinitionId());
        command.setPerformingOrgId(request.getPerformingOrgId());
        command.setQuantityValue(request.getQuantityValue());
        command.setPriority(request.getPriority());
        command.setRemark(request.getRemark());
        command.setBusinessTime(request.getBusinessTime());
        command.setBillingPractitionerId(request.getBillingPractitionerId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 删除配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public DeleteAutoGenRuleCommand toDeleteAutoGenRuleCommand(DeleteAutoGenRuleRequest request, Long operatorId) {
        DeleteAutoGenRuleCommand command = new DeleteAutoGenRuleCommand();
        command.setRuleId(request.getRuleId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 启用配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public ActivateAutoGenRuleCommand toActivateAutoGenRuleCommand(ActivateAutoGenRuleRequest request, Long operatorId) {
        ActivateAutoGenRuleCommand command = new ActivateAutoGenRuleCommand();
        command.setRuleId(request.getRuleId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 停用配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public SuspendAutoGenRuleCommand toSuspendAutoGenRuleCommand(SuspendAutoGenRuleRequest request, Long operatorId) {
        SuspendAutoGenRuleCommand command = new SuspendAutoGenRuleCommand();
        command.setRuleId(request.getRuleId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 新增配置成功后 ensure 长期滚费 SR 命令（categoryEnum 由 AppService 按选品回填）。
     *
     * @param request    新增请求
     * @param ruleId     配置主键
     * @param operatorId 操作人
     * @return ensure 命令
     */
    @Override
    public EnsureAutoRollBedLongTermServiceRequestCommand toEnsureAutoRollBedLongTermServiceRequestCommand(
        CreateAutoGenRuleRequest request,
        Long ruleId,
        Long operatorId) {
        EnsureAutoRollBedLongTermServiceRequestCommand command = new EnsureAutoRollBedLongTermServiceRequestCommand();
        command.setRuleId(ruleId);
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(request.getPatientId());
        command.setActivityId(request.getActivityId());
        command.setQuantity(request.getQuantityValue());
        command.setRequesterId(operatorId);
        command.setOperatorId(operatorId);
        return command;
    }
}
