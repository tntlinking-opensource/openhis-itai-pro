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

package com.openhis.health.application.basic.registrationtype.service;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.registrationtype.dto.RegistrationTypeDto;
import com.openhis.health.application.basic.registrationtype.mapper.RegistrationTypeMapper;
import com.openhis.health.application.basic.registrationtype.request.CreateRegistrationTypeRequest;
import com.openhis.health.application.basic.registrationtype.request.UpdateRegistrationTypeRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.healthcareservice.command.AbolishHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ReactivateHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.RetireHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceDomainService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础数据「挂号类型」应用服务：对外以医疗服务（HealthcareService）承载，同步维护挂在该服务实例上的销售价定价行。
 *
 * <p>金额在 DTO 中以「元」展示，持久化通过 {@link #toPersistedAmount(BigDecimal)} 转为分整型写入收费定义；删除/作废时清理实例定价。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationTypeAppService {
    /** 无登录上下文时的系统操作人占位主键。 */
    private static final Long SYSTEM_OPERATOR_ID = 0L;
    /** 作废服务项原因文案（写入领域命令）。 */
    private static final String REASON_DELETE_BY_APP = "应用层删除挂号类型";
    /** 停用服务项原因文案。 */
    private static final String REASON_DEACTIVATE_BY_APP = "应用层停用挂号类型";

    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final HealthcareServiceDomainService healthcareServiceDomainService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final RegistrationTypeMapper registrationTypeMapper;

    /**
     * 列出全部挂号类型（注册场景下的医疗服务），并为每条补充当前销售价金额（元）。
     *
     * @return DTO 列表
     */
    public List<RegistrationTypeDto> getRegistrationTypeList() {
        List<HealthcareService> all = healthcareServiceQueryService.listServicesForRegistration();
        List<RegistrationTypeDto> result = new ArrayList<>();
        for (HealthcareService entity : all) {
            if (entity == null) {
                continue;
            }
            RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
            fillAmountByServiceId(entity.getId(), dto);
            result.add(dto);
        }
        return result;
    }

    /**
     * 按主键加载单条挂号类型并填充金额。
     *
     * @param id 医疗服务主键
     * @return DTO
     * @throws ValidationException id 为空时
     * @throws EntityNotFoundException 记录不存在时
     */
    public RegistrationTypeDto getRegistrationType(Long id) {
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(id);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", id);
        }
        RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
        fillAmountByServiceId(id, dto);
        return dto;
    }

    /**
     * 新增挂号类型：创建医疗服务后按请求金额写入或跳过实例定价。
     *
     * @param request 创建请求
     * @return 含金额的 DTO
     * @throws ValidationException 请求体为空时
     */
    public RegistrationTypeDto createRegistrationType(CreateRegistrationTypeRequest request) {
        if (request == null) {
            throw new ValidationException("挂号类型请求不能为空");
        }
        Long serviceId = healthcareServiceDomainService.createService(
            registrationTypeMapper.toCreateCommand(request, SYSTEM_OPERATOR_ID));
        replacePricing(serviceId, request.getAmount());
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(serviceId);
        RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
        fillAmountByServiceId(serviceId, dto);
        return dto;
    }

    /**
     * 更新挂号类型基础信息并重写实例定价（金额 {@code null} 时仅清空后不再写销售行）。
     *
     * @param request 更新请求
     * @return 含金额的 DTO
     * @throws ValidationException 请求或 id 不合法时
     */
    public RegistrationTypeDto updateRegistrationType(UpdateRegistrationTypeRequest request) {
        if (request == null) {
            throw new ValidationException("挂号类型请求不能为空");
        }
        if (request.getId() == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        healthcareServiceDomainService.modifyService(
            registrationTypeMapper.toUpdateCommand(request, SYSTEM_OPERATOR_ID));
        replacePricing(request.getId(), request.getAmount());
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(request.getId());
        RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
        fillAmountByServiceId(request.getId(), dto);
        return dto;
    }

    /**
     * 作废挂号类型并移除其下全部实例定价。
     *
     * @param id 医疗服务主键
     * @throws ValidationException id 为空时
     * @throws EntityNotFoundException 记录不存在时
     */
    public void deleteRegistrationType(Long id) {
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(id);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", id);
        }
        AbolishHealthcareServiceCommand command = new AbolishHealthcareServiceCommand();
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        AbolishHealthcareServiceCommand.Item item = new AbolishHealthcareServiceCommand.Item();
        item.setServiceId(id);
        item.setReason(REASON_DELETE_BY_APP);
        command.setItem(item);
        healthcareServiceDomainService.abolishService(command);
        replacePricing(id, null);
    }

    /**
     * 重新启用已停用的挂号类型并回读详情。
     *
     * @param id 医疗服务主键
     * @return 最新 DTO
     * @throws ValidationException id 为空时
     */
    public RegistrationTypeDto activateRegistrationType(Long id) {
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        ReactivateHealthcareServiceCommand command = new ReactivateHealthcareServiceCommand();
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        ReactivateHealthcareServiceCommand.Item item = new ReactivateHealthcareServiceCommand.Item();
        item.setServiceId(id);
        command.setItem(item);
        healthcareServiceDomainService.reactivateService(command);
        return getRegistrationType(id);
    }

    /**
     * 停用挂号类型（退休状态）并回读详情；不自动删除定价行。
     *
     * @param id 医疗服务主键
     * @return 最新 DTO
     * @throws ValidationException id 为空时
     */
    public RegistrationTypeDto deactivateRegistrationType(Long id) {
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        RetireHealthcareServiceCommand command = new RetireHealthcareServiceCommand();
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        RetireHealthcareServiceCommand.Item item = new RetireHealthcareServiceCommand.Item();
        item.setServiceId(id);
        item.setReason(REASON_DEACTIVATE_BY_APP);
        command.setItem(item);
        healthcareServiceDomainService.retireService(command);
        return getRegistrationType(id);
    }

    /**
     * 从收费定义中解析该医疗服务实例的销售/单价行金额，写入 DTO 的 amount（元）。
     *
     * @param serviceId 医疗服务主键
     * @param dto       待填充 DTO
     */
    private void fillAmountByServiceId(Long serviceId, RegistrationTypeDto dto) {
        if (serviceId == null || dto == null) {
            return;
        }
        ChargeItemDefinition saleItem = findSalePriceLine(serviceId);
        if (saleItem == null || saleItem.getPriceValue() == null) {
            dto.setAmount(null);
            return;
        }
        dto.setAmount(saleItem.getPriceValue());
    }

    /**
     * 查询挂在指定医疗服务实例上的定价行，命中零售价（{@link PriceType#SALE}）码即返回。
     *
     * @param serviceId 医疗服务主键
     * @return 命中的定价行或 {@code null}
     */
    private ChargeItemDefinition findSalePriceLine(Long serviceId) {
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        queryCommand.setInstanceId(serviceId);
        List<ChargeItemDefinition> items = chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
        for (ChargeItemDefinition item : items) {
            if (item == null) {
                continue;
            }
            if (PriceType.SALE.getCode().equals(item.getPriceTypeCode())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 先按实例清空全部定价行，再在金额非空时写入一条销售价分整型定价。
     *
     * @param serviceId 医疗服务主键
     * @param amount    销售金额（元）；{@code null} 表示仅删除不写回
     */
    private void replacePricing(Long serviceId, BigDecimal amount) {
        RemovePricingByInstanceCommand removeCommand = new RemovePricingByInstanceCommand();
        removeCommand.setOperatorId(SYSTEM_OPERATOR_ID);
        removeCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        removeCommand.setInstanceId(serviceId);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCommand);
        if (amount == null) {
            return;
        }
        SaveAllChargeItemDefinitionsCommand saveCommand = new SaveAllChargeItemDefinitionsCommand();
        saveCommand.setOperatorId(SYSTEM_OPERATOR_ID);
        SaveAllChargeItemDefinitionsCommand.Item item = new SaveAllChargeItemDefinitionsCommand.Item();
        item.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        item.setInstanceId(serviceId);
        item.setPriceTypeCode(PriceType.SALE.getCode());
        item.setPriceValue(amount.setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP));
        saveCommand.setItem(item);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(saveCommand);
    }

}
