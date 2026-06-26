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
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromAutoRollCommand;

/**
 * AS-IP-NUR-013：自动计费 Request ↔ 领域 Command / 滚费 DTO 映射。
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
     * 滚费行 → 建单命令（单条批次）。
     *
     * @param line       落账行
     * @param operatorId 操作人
     * @return 滚费建单命令
     */
    CreateFromAutoRollCommand toCreateFromAutoRollCommand(AutoRollLineContext line, Long operatorId);

    /**
     * 滚费行 → 收费明细命令。
     *
     * @param line              落账行
     * @param serviceRequestId  新建诊疗申请 id
     * @param operatorId        操作人
     * @return 收费命令
     */
    CreateChargeItemCommand toCreateChargeItemCommandForAutoRoll(
        AutoRollLineContext line,
        Long serviceRequestId,
        Long operatorId);
}
