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
import com.zxplat.domain.workflow.servicerequest.command.EnsureAutoRollBedLongTermServiceRequestCommand;

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
     * 新增配置成功后 ensure 长期滚费 SR 命令（categoryEnum 由 AppService 按选品回填）。
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
