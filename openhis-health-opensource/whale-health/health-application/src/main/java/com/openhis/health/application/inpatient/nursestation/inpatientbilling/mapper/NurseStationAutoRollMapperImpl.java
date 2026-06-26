/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ActivateAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.CreateAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.DeleteAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.SuspendAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.UpdateAutoGenRuleRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.ActivateAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.CreateAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.DeleteAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.SuspendAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.UpdateAutoGenRuleCommand;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromAutoRollCommand;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

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
        // 床位服务固定 trigger 表名（BR-IP-ARG-003）
        command.setTriggerSourceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        command.setTriggerSourceId(request.getActivityId());
        command.setChargeDefinitionId(request.getChargeDefinitionId());
        command.setPerformingOrgId(request.getPerformingOrgId());
        command.setQuantityValue(request.getQuantityValue());
        command.setPriority(request.getPriority());
        command.setRemark(request.getRemark());
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
     * 滚费行 → 建单命令（单条）。
     *
     * @param line       落账行
     * @param operatorId 操作人
     * @return 滚费建单命令
     */
    @Override
    public CreateFromAutoRollCommand toCreateFromAutoRollCommand(AutoRollLineContext line, Long operatorId) {
        CreateFromAutoRollCommand command = new CreateFromAutoRollCommand();
        command.setOperatorId(operatorId);
        CreateFromAutoRollCommand.CreateFromAutoRollItemCommand item =
            new CreateFromAutoRollCommand.CreateFromAutoRollItemCommand();
        item.setEncounterId(line.getEncounterId());
        item.setPatientId(line.getPatientId());
        item.setActivityId(line.getActivityId());
        item.setQuantity(line.getQuantity());
        item.setUnitCode(line.getUnitCode());
        item.setRequesterId(line.getRequesterId());
        item.setCategoryEnum(line.getCategoryEnum());
        item.setOccurrenceTime(line.getOccurrenceTime());
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 滚费行 → 收费明细命令。
     *
     * @param line             落账行
     * @param serviceRequestId 诊疗申请 id
     * @param operatorId       操作人
     * @return 收费命令
     */
    @Override
    public CreateChargeItemCommand toCreateChargeItemCommandForAutoRoll(
        AutoRollLineContext line,
        Long serviceRequestId,
        Long operatorId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(line.getPatientId());
        item.setEncounterId(line.getEncounterId());
        item.setAccountId(line.getAccountId());
        item.setDefinitionId(line.getChargeDefinitionId());
        item.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
        item.setServiceId(serviceRequestId);
        item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setProductId(line.getActivityId());
        item.setUnitPrice(line.getUnitPrice());
        item.setQuantityValue(line.getQuantity());
        item.setQuantityUnit(StringUtils.defaultIfBlank(line.getUnitCode(), ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT));
        item.setTotalPrice(line.getTotalPrice());
        item.setBaseAmount(line.getTotalPrice());
        item.setDiscountAmount(line.getTotalPrice());
        item.setSurchargeAmount(BigDecimal.ZERO);
        item.setOccurrenceTime(line.getOccurrenceTime());
        item.setOrgId(line.getOrganizationId());
        item.setRequestingOrgId(line.getOrganizationId());
        // 滚费来源与护士划价区分（BR-IP-ARG-015）
        item.setGenerateSourceEnum(GenerateSource.AUTO_ROLL_FEES.getCode());
        item.setContextEnum(ChargeItemDmConstants.CONTEXT_ACTIVITY);
        command.setItems(List.of(item));
        return command;
    }
}
