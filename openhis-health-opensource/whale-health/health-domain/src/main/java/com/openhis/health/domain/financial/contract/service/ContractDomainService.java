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

package com.openhis.health.domain.financial.contract.service;

import com.openhis.health.domain.financial.contract.command.*;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.repository.ContractRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 费用性质（合同/结算口径）写侧领域服务。
 *
 * <p>维护机构维度费用性质主数据：建档、编辑、软删及启停；业务编码（busNo）在机构内唯一。</p>
 */
@Service
@RequiredArgsConstructor
public class ContractDomainService {
    private final ContractRepository contractRepository;


    /**
     * 新建费用性质：校验编码唯一，装配终端配置、初始化启用态与检索码后落库。
     *
     * @param command 建档命令
     * @return 新记录主键
     * @throws BusinessException 业务编码与其他记录冲突时
     */
    public Long createContract(CreateContractCommand command) {
        this.assertBusNoUnique(command.getBusNo(), null);
        Contract contract = new Contract();
        contract.setBusNo(command.getBusNo());
        contract.setName(command.getName());
        contract.setCategoryEnum(command.getCategoryEnum());
        contract.setDisplayOrder(command.getDisplayOrder());
        contract.setYbFlag(command.getYbFlag());
        contract.setAdmVs(command.getAdmVs());
        contract.setOrgId(command.getOrgId());
        this.applyTerminalConfig(contract, command.getTerminalConfig());
        contract.assertBusNoNotBlank();
        contract.assertDisplayOrderNonNegative();
        contract.initActive();
        contract.refreshSearchCodeFromName();
        Contract saved = contractRepository.save(contract);
        return saved.getId();
    }

    /**
     * 编辑费用性质：重新校验编码唯一（排除自身），更新展示与终端等字段并刷新检索码。
     *
     * @param command 编辑命令
     * @throws BusinessException 记录不存在或编码冲突时
     */
    public void editContract(EditContractCommand command) {
        // Pre：按主键加载费用性质实体，不存在则抛出业务异常
        Contract contract = this.mustGetContract(command.getId());
        this.assertBusNoUnique(command.getBusNo(), command.getId());
        contract.setBusNo(command.getBusNo());
        contract.setName(command.getName());
        contract.setCategoryEnum(command.getCategoryEnum());
        contract.setDisplayOrder(command.getDisplayOrder());
        contract.setYbFlag(command.getYbFlag());
        contract.setAdmVs(command.getAdmVs());
        this.applyTerminalConfig(contract, command.getTerminalConfig());
        contract.assertBusNoNotBlank();
        contract.assertDisplayOrderNonNegative();
        contract.refreshSearchCodeFromName();
        contractRepository.save(contract);
    }

    /**
     * 软删费用性质。
     *
     * @param command 须含主键与操作人
     * @throws BusinessException 记录不存在时
     */
    public void deleteContract(DeleteContractCommand command) {
        // Pre：按主键加载费用性质实体，不存在则抛出业务异常
        Contract contract = this.mustGetContract(command.getId());
        contract.markDeleted(command.getOperatorId());
        contractRepository.save(contract);
    }

    /**
     * 启用费用性质。
     *
     * @param command 须含主键
     * @throws BusinessException 记录不存在时
     */
    public void activateContract(ActivateContractCommand command) {
        // Pre：按主键加载费用性质实体，不存在则抛出业务异常
        Contract contract = this.mustGetContract(command.getId());
        contract.activate();
        contractRepository.save(contract);
    }

    /**
     * 停用费用性质。
     *
     * @param command 须含主键
     * @throws BusinessException 记录不存在时
     */
    public void deactivateContract(DeactivateContractCommand command) {
        // Pre：按主键加载费用性质实体，不存在则抛出业务异常
        Contract contract = this.mustGetContract(command.getId());
        contract.deactivate();
        contractRepository.save(contract);
    }

    /**
     * 按主键加载费用性质实体，不存在则抛出业务异常。
     *
     * @param id 费用性质主键
     * @return 费用性质聚合根实体
     * @throws BusinessException 当记录不存在时
     */
    private Contract mustGetContract(Long id) {
        return contractRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.CONTRACT_NOT_FOUND, "费用性质不存在"));
    }

    /**
     * 校验业务编码在库内唯一；{@code id} 非空时排除当前记录（更新场景）。
     *
     * @param busNo 待校验的业务编码
     * @param id 当前记录主键（更新场景传主键，新建场景传 null）
     * @throws BusinessException 当业务编码与现有记录冲突时
     */
    private void assertBusNoUnique(String busNo, Long id) {
        // 业务分支处理：新建场景，仅校验全局唯一性
        if (id == null) {
            if (contractRepository.findByBusNo(busNo).isPresent()) {
                throw new BusinessException(ErrorCodeConstants.CONTRACT_BUS_NO_CONFLICT, "费用性质编码冲突");
            }
            return;
        }
        // 业务分支处理：更新场景，排除自身后校验唯一性
        if (contractRepository.findByBusNoAndIdNot(busNo, id).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.CONTRACT_BUS_NO_CONFLICT, "费用性质编码冲突");
        }
    }

    /**
     * 将终端/医保对接等配置写入聚合；命令为空则不改写字段。
     *
     * @param contract 费用性质聚合根实体
     * @param terminalConfig 终端配置命令，可能为 null
     */
    private void applyTerminalConfig(Contract contract, ContractTerminalConfigCommand terminalConfig) {
        // 业务分支处理：终端配置为空时直接返回，保持既有值不变
        if (terminalConfig == null) {
            return;
        }
        contract.setClientId(terminalConfig.getClientId());
        contract.setClientSecret(terminalConfig.getClientSecret());
        contract.setUsername(terminalConfig.getUsername());
        contract.setPassword(terminalConfig.getPassword());
        contract.setCliPrvKey(terminalConfig.getCliPrvKey());
        contract.setCliPubKey(terminalConfig.getCliPubKey());
        contract.setServerPubKey(terminalConfig.getServerPubKey());
        contract.setScope(terminalConfig.getScope());
        contract.setGrantType(terminalConfig.getGrantType());
        contract.setFixmedinsCode(terminalConfig.getFixmedinsCode());
        contract.setFixmedinsName(terminalConfig.getFixmedinsName());
    }
}
