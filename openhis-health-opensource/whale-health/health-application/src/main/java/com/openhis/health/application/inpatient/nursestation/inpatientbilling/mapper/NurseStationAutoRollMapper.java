/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ActivateAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.CreateAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.DeleteAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.SuspendAutoGenRuleRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.UpdateAutoGenRuleRequest;
import com.openhis.health.domain.custom.chargeitemautogen.command.ActivateAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.CreateAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.DeleteAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.SuspendAutoGenRuleCommand;
import com.openhis.health.domain.custom.chargeitemautogen.command.UpdateAutoGenRuleCommand;
import com.openhis.health.domain.workflow.servicerequest.command.EnsureAutoRollBedLongTermServiceRequestCommand;

/**
 * AS-IP-NUR-013：自动计费 Request ↔ 领域 Command 映射。
 */
public interface NurseStationAutoRollMapper {

    /**
     * 新增配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令（无 id）
     */
    CreateAutoGenRuleCommand toCreateAutoGenRuleCommand(CreateAutoGenRuleRequest request, Long operatorId);

    /**
     * 更新配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    UpdateAutoGenRuleCommand toUpdateAutoGenRuleCommand(UpdateAutoGenRuleRequest request, Long operatorId);

    /**
     * 删除配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    DeleteAutoGenRuleCommand toDeleteAutoGenRuleCommand(DeleteAutoGenRuleRequest request, Long operatorId);

    /**
     * 启用配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    ActivateAutoGenRuleCommand toActivateAutoGenRuleCommand(ActivateAutoGenRuleRequest request, Long operatorId);

    /**
     * 停用配置请求 → 领域命令。
     *
     * @param request    HTTP 请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    SuspendAutoGenRuleCommand toSuspendAutoGenRuleCommand(SuspendAutoGenRuleRequest request, Long operatorId);

    /**
     * 新增配置成功后 ensure 长期床位 SR 命令。
     *
     * @param request    新增请求
     * @param ruleId     配置主键
     * @param operatorId 操作人
     * @return ensure 命令
     */
    EnsureAutoRollBedLongTermServiceRequestCommand toEnsureAutoRollBedLongTermServiceRequestCommand(
        CreateAutoGenRuleRequest request,
        Long ruleId,
        Long operatorId);
}
