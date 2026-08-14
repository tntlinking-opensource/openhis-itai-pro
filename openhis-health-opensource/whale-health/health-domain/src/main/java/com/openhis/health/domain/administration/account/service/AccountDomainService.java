/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.account.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.openhis.health.domain.administration.account.command.SaveAccountByRegisterCommand;
import com.openhis.health.domain.administration.account.command.SaveOrUpdateAccountCommand;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.repository.AccountRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientPrepayConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.AccountBillingStatus;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.events.administration.account.AccountDomainEvents;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 就诊账户（{@code adm_account}）聚合写侧领域服务。
 *
 * <p>职责：开立账户、按挂号落库、余额与默认标志更新。</p>
 * <p>新建路径在装配 {@link Account} 后调用实体 {@link Account#assertPersistableNewRow()}，
 * 将患者/就诊/类型/业务号非空约束收口在聚合根。</p>
 * <p>不变量：同就诊同类型账户幂等开立；余额变更须发布事件；持久化先于账户领域事件（B.14）。</p>
 * <p>仅注入本聚合 {@link AccountRepository}；跨聚合费用性质编码由应用层传入。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountDomainService {
    private final AccountRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 就诊账户 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#ACCOUNT}。 */
    private final BusinessSequenceService businessSequenceService;


    /**
     * 批量开立就诊账户并发布账户已开立事件。
     *
     * @param cmd 批量开立命令，含多条患者/就诊/账户类型
     */
    public void openEncounterAccounts(OpenEncounterAccountsCommand cmd) {
        // Pre：解析命令中的批量开立项，获取待处理条目列表。
        List<OpenEncounterAccountsCommand.Item> items = cmd.resolveItems();
        // Pre：按业务日批量生成业务流水号，用于就诊账户唯一标识。
        List<String> busNos = businessSequenceService.getSeqByDay(AggregateTypeConstants.ACCOUNT, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, items.size());
        List<Account> toSave = new ArrayList<>();
        // Loop：遍历命令项，逐条组装账户实体。
        for (int index = 0; index < items.size(); index++) {
            OpenEncounterAccountsCommand.Item item = items.get(index);
            Account row = new Account();
            // 基础关联信息：患者、就诊与账户类型。
            row.setPatientId(item.getPatientId());
            row.setEncounterId(item.getEncounterId());
            row.setTypeCode(item.getTypeCode());
            row.setDefaultFlag(item.getDefaultFlag());
            row.setContractNo(item.getContractNo());
            if (StringUtils.isNotEmpty(item.getInsutype())) {
                row.setInsutype(item.getInsutype().trim());
            }
            this.applyNewAccountStatusDefaults(row);
            // 新开账户余额归零。
            row.setBalance(BigDecimal.ZERO);
            row.setBusNo(busNos.get(index));
            row.assertPersistableNewRow();
            toSave.add(row);
        }
        List<Account> saved = repository.saveAll(toSave);
        // Event：逐条发布账户开立领域事件，通知下游读模型同步。
        for (Account row : saved) {
            AccountDomainEvents.AccountOpenedEvent event =
                new AccountDomainEvents.AccountOpenedEvent(row.getId(), row.getEncounterId(), row.getTypeCode());
            event.setPatientId(row.getPatientId());
            event.setPatientName(row.getName());
            event.setBalanceAmount(row.getBalance());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 挂号场景下单条开立账户并发布账户已开立事件。
     *
     * @param cmd 挂号开立命令
     */
    public void saveAccountByRegister(SaveAccountByRegisterCommand cmd) {
        // Pre：同就诊同类型账户已存在则幂等跳过（住院登记与预交金补开场景）；空 insutype 可补写。
        Optional<Account> exists = repository.findFirstByPatientIdAndEncounterIdAndTypeCode(
            cmd.getPatientId(), cmd.getEncounterId(), cmd.getTypeCode());
        if (exists.isPresent()) {
            this.backfillInsutypeIfMissing(exists.get(), cmd.getInsutype());
            return;
        }
        // Pre：组装单条挂号场景账户实体。
        Account row = new Account();
        // 基础关联信息：患者、就诊与账户类型。
        row.setPatientId(cmd.getPatientId());
        row.setEncounterId(cmd.getEncounterId());
        row.setTypeCode(cmd.getTypeCode());
        row.setDefaultFlag(cmd.getDefaultFlag());
        row.setContractNo(cmd.getContractNo());
        if (StringUtils.isNotEmpty(cmd.getInsutype())) {
            row.setInsutype(cmd.getInsutype().trim());
        }
        this.applyNewAccountStatusDefaults(row);
        // 新开账户余额归零。
        row.setBalance(BigDecimal.ZERO);
        // Pre：按业务日生成唯一业务流水号。
        row.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.ACCOUNT, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        row.assertPersistableNewRow();
        row = repository.save(row);
        // Event：发布账户开立领域事件，通知下游读模型同步。
        AccountDomainEvents.AccountOpenedEvent event =
            new AccountDomainEvents.AccountOpenedEvent(row.getId(), row.getEncounterId(), row.getTypeCode());
        event.setPatientId(row.getPatientId());
        event.setPatientName(row.getName());
        event.setBalanceAmount(row.getBalance());
        domainEventPublisher.publish(event);
    }

    /**
     * 账户已开立但 {@code contract_no} 为空时，按费用性质 {@code bus_no} 补写（不覆盖已有值）。
     *
     * @param accountId  账户主键
     * @param contractNo 费用性质业务编码（{@code fin_contract.bus_no}）
     */
    public void backfillContractNoIfMissing(Long accountId, String contractNo) {
        if (accountId == null || StringUtils.isEmpty(contractNo)) {
            return;
        }
        Optional<Account> optional = repository.findById(accountId);
        if (optional.isEmpty()) {
            return;
        }
        Account row = optional.get();
        if (StringUtils.isNotEmpty(row.getContractNo())) {
            return;
        }
        row.setContractNo(contractNo.trim());
        repository.save(row);
    }

    /**
     * 账户已开立但 {@code insutype} 为空时补写（不覆盖已有值）。
     *
     * @param row      已存在账户
     * @param insutype 险种码；空则跳过
     */
    private void backfillInsutypeIfMissing(Account row, String insutype) {
        if (row == null || StringUtils.isEmpty(insutype)) {
            return;
        }
        if (StringUtils.isNotEmpty(row.getInsutype())) {
            return;
        }
        row.setInsutype(insutype.trim());
        repository.save(row);
    }

    /**
     * 住院登记开立默认就诊账户（余额 0，携带费用性质编码）；预交金到账后走 {@link #saveOrUpdateAccount} 等更新。
     *
     * @param patientId 患者主键
     * @param encounterId 住院就诊主键
     * @param contractNo 费用性质 {@code bus_no}
     * @param operatorId 操作人
     */
    public void openInpatientRegAccount(Long patientId, Long encounterId, String contractNo, Long operatorId) {
        if (StringUtils.isEmpty(contractNo)) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "费用性质编码不能为空");
        }
        SaveAccountByRegisterCommand cmd = new SaveAccountByRegisterCommand();
        cmd.setPatientId(patientId);
        cmd.setEncounterId(encounterId);
        cmd.setContractNo(contractNo.trim());
        cmd.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        cmd.setDefaultFlag(Boolean.TRUE);
        cmd.setOperatorId(operatorId);
        this.saveAccountByRegister(cmd);
    }

    /**
     * 按患者、就诊与账户类型保存或更新账户；不存在则新建并发布开立事件，存在则更新余额并视情况发布余额变更事件。
     *
     * @param cmd 保存或更新命令
     */
    public void saveOrUpdateAccount(SaveOrUpdateAccountCommand cmd) {
        // Pre：按患者、就诊与账户类型查询是否已存在账户。
        Optional<Account> exists = repository.findFirstByPatientIdAndEncounterIdAndTypeCode(
            cmd.getPatientId(), cmd.getEncounterId(), cmd.getTypeCode());
        // 患者账户已存在则更新余额，不存在则新建。
        if (exists.isPresent()) {
            Account row = exists.get();
            // Pre：记录旧余额，用于后续判断是否需要发布余额变更事件。
            BigDecimal oldBalance = row.getBalance();
            row.setBalance(cmd.getBalance());
            row.setDefaultFlag(cmd.getDefaultFlag());
            repository.save(row);
            // 余额发生变更时发布余额编辑事件，供读侧同步账户摘要。
            if (oldBalance == null || !oldBalance.equals(cmd.getBalance())) {
                AccountDomainEvents.AccountBalanceEditedEvent event =
                    new AccountDomainEvents.AccountBalanceEditedEvent(row.getId(), oldBalance, cmd.getBalance());
                event.setEncounterId(row.getEncounterId());
                domainEventPublisher.publish(event);
            }
            return;
        }
        // 账户不存在时新建聚合并初始化余额。
        Account row = new Account();
        // 基础关联信息：患者、就诊与账户类型。
        row.setPatientId(cmd.getPatientId());
        row.setEncounterId(cmd.getEncounterId());
        row.setTypeCode(cmd.getTypeCode());
        row.setDefaultFlag(cmd.getDefaultFlag());
        this.applyNewAccountStatusDefaults(row);
        // 按命令设置初始余额。
        row.setBalance(cmd.getBalance());
        // Pre：按业务日生成唯一业务流水号。
        row.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.ACCOUNT, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        row.assertPersistableNewRow();
        row = repository.save(row);
        // Event：发布账户开立领域事件，通知下游读模型同步。
        AccountDomainEvents.AccountOpenedEvent event =
            new AccountDomainEvents.AccountOpenedEvent(row.getId(), row.getEncounterId(), row.getTypeCode());
        event.setPatientId(row.getPatientId());
        event.setPatientName(row.getName());
        event.setBalanceAmount(row.getBalance());
        domainEventPublisher.publish(event);
    }

    /**
     * 住院预交金支付结算成功后，按增量更新就诊个人现金户余额并发布余额变更事件（DM-FIN-002 §9.1、BR-IP-PREP-005）。
     *
     * @param cmd 患者/就诊、余额增量（收取为正、退费为负）、操作人
     * @throws BusinessException 账户不存在（EX-IP-PREP-002）或退费后余额非法（EX-IP-PREP-005）时
     */
    public void adjustAdvanceDepositBalance(AdjustAdvanceDepositBalanceCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "命令不能为空");
        }
        BigDecimal delta = cmd.getBalanceDelta();
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        Account account = this.requireActivePersonalCashAccount(cmd.getPatientId(), cmd.getEncounterId());
        BigDecimal current = account.getBalance();
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        BigDecimal nextBalance = current.add(delta);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_005,
                "退费金额大于当前账户余额");
        }
        SaveOrUpdateAccountCommand updateCmd = new SaveOrUpdateAccountCommand();
        updateCmd.setPatientId(cmd.getPatientId());
        updateCmd.setEncounterId(cmd.getEncounterId());
        updateCmd.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        updateCmd.setBalance(nextBalance);
        updateCmd.setDefaultFlag(account.getDefaultFlag());
        updateCmd.setOperatorId(cmd.getOperatorId());
        this.saveOrUpdateAccount(updateCmd);
    }

    /**
     * 新开账户默认主状态与结账状态（有效 + 可用）。
     *
     * @param row 待落库账户行
     */
    private void applyNewAccountStatusDefaults(Account row) {
        // 主状态：有效/已启用（PublicationStatus，表达账户行是否可用）。
        row.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        // 结账状态：开放可记账（AccountBillingStatus）。
        row.setBillingStatusEnum(AccountBillingStatus.OPEN.getCode());
    }

    /**
     * 加载患者就诊下有效个人现金账户；与 {@link AccountQueryService#getRegistrationAccount} 口径一致。
     */
    private Account requireActivePersonalCashAccount(Long patientId, Long encounterId) {
        Optional<Account> optional = repository.findFirstByPatientIdAndEncounterIdAndTypeCode(
            patientId,
            encounterId,
            AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        Account account = optional.filter(this::isEffectiveActiveAccount).orElse(null);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_002,
                "个人现金账户不存在，无法更新预交金余额");
        }
        return account;
    }

    /**
     * 账户是否有效激活（statusEnum=ACTIVE）。
     *
     * @param row 账户行
     * @return true 表示可用
     */
    private boolean isEffectiveActiveAccount(Account row) {
        if (row == null) {
            return false;
        }
        return PublicationStatus.ACTIVE.getCode().equals(row.getStatusEnum());
    }
}
