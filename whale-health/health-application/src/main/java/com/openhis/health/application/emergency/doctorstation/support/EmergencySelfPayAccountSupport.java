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

package com.openhis.health.application.emergency.doctorstation.support;

import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 急诊就诊自费账户：分诊/入科未开户时，在医嘱费用编排前幂等开立默认个人现金账户。
 */
@Service
@RequiredArgsConstructor
public class EmergencySelfPayAccountSupport {

    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;

    /**
     * 解析就诊默认自费账户；不存在则开立（与门诊挂号预结算开户口径一致）。
     *
     * @param encounterId 就诊标识
     * @param patientId   患者标识
     * @param operatorId  操作人（审计）
     * @return 自费账户主键
     */
    public Long requireSelfPayAccountId(Long encounterId, Long patientId, Long operatorId) {
        Long accountId = accountQueryService.getSelfPayAccount(encounterId);
        if (accountId != null) {
            return accountId;
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (registrationAccount != null && registrationAccount.getId() != null) {
            return registrationAccount.getId();
        }
        OpenEncounterAccountsCommand command = new OpenEncounterAccountsCommand();
        command.setOperatorId(operatorId);
        OpenEncounterAccountsCommand.Item item = new OpenEncounterAccountsCommand.Item();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setDefaultFlag(Boolean.TRUE);
        item.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        command.setItem(item);
        accountDomainService.openEncounterAccounts(command);
        accountId = accountQueryService.getSelfPayAccount(encounterId);
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "开立就诊自费账户失败，无法生成费用草稿");
        }
        return accountId;
    }
}
