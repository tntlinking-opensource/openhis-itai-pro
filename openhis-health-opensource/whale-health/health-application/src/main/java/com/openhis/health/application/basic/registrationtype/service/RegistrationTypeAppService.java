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

import com.openhis.health.module.common.assembler.pricing.ChargePricingCommandAssembler;
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
import com.openhis.health.domain.administration.healthcareservice.command.PublishHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ReactivateHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.RetireHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceDomainService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据「挂号类型」应用服务：对外以医疗服务（HealthcareService）承载，同步维护挂在该服务实例上的销售价定价行。
 *
 * <p>金额在 DTO 与 {@code adm_charge_item_definition.price_value} 中均以「元」口径读写；新建/编辑须同步写入
 * {@code instance_table=adm_healthcare_service} 的 {@link PriceType#SALE} 定价行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationTypeAppService {
    /** 作废服务项原因文案（写入领域命令）。 */
    private static final String REASON_DELETE_BY_APP = "应用层删除挂号类型";
    /** 停用服务项原因文案。 */
    private static final String REASON_DEACTIVATE_BY_APP = "应用层停用挂号类型";

    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final HealthcareServiceDomainService healthcareServiceDomainService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final RegistrationTypeMapper registrationTypeMapper;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 列出全部挂号类型（注册场景下的医疗服务），并为每条补充当前销售价金额（元）。
     *
     * @return DTO 列表
     */
    public List<RegistrationTypeDto> getRegistrationTypeList() {
        List<HealthcareService> all = healthcareServiceQueryService.listServicesForRegistration();
        Map<Long, BigDecimal> saleAmountByServiceId = loadSaleAmountMap(all);
        List<RegistrationTypeDto> result = new ArrayList<>();
        for (HealthcareService entity : all) {
            if (entity == null) {
                continue;
            }
            RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
            if (entity.getId() != null) {
                dto.setAmount(saleAmountByServiceId.get(entity.getId()));
            }
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
     * 新增挂号类型：创建医疗服务并直接发布为 ACTIVE，同步写入销售价定价行。
     *
     * @param request 创建请求
     * @return 含金额的 DTO
     * @throws ValidationException 请求体为空或金额缺失时
     */
    public RegistrationTypeDto createRegistrationType(CreateRegistrationTypeRequest request) {
        if (request == null) {
            throw new ValidationException("挂号类型请求不能为空");
        }
        if (request.getAmount() == null) {
            throw new ValidationException("挂号类型金额不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再新增挂号类型");
        Long serviceId = healthcareServiceDomainService.createService(
            registrationTypeMapper.toCreateCommand(request, operatorId));
        PublishHealthcareServiceCommand publishCommand = new PublishHealthcareServiceCommand();
        publishCommand.setOperatorId(operatorId);
        PublishHealthcareServiceCommand.Item publishItem = new PublishHealthcareServiceCommand.Item();
        publishItem.setServiceId(serviceId);
        publishCommand.setItem(publishItem);
        healthcareServiceDomainService.publishService(publishCommand);
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(serviceId);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", serviceId);
        }
        // 新建号源须落库诊察费定价行（fin_type 1012），否则日结/费用明细无法汇总。
        syncSalePricingForHealthcareService(
            entity.getId(),
            DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE,
            null,
            entity.getName(),
            request.getAmount(),
            operatorId);
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再更新挂号类型");
        healthcareServiceDomainService.modifyService(
            registrationTypeMapper.toUpdateCommand(request, operatorId));
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(request.getId());
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", request.getId());
        }
        // 编辑时按请求金额重建实例定价；type_code 固定诊察费 1012。
        syncSalePricingForHealthcareService(
            entity.getId(),
            DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE,
            null,
            entity.getName(),
            request.getAmount(),
            operatorId);
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再删除挂号类型");
        AbolishHealthcareServiceCommand command = new AbolishHealthcareServiceCommand();
        command.setOperatorId(operatorId);
        AbolishHealthcareServiceCommand.Item item = new AbolishHealthcareServiceCommand.Item();
        item.setServiceId(id);
        item.setReason(REASON_DELETE_BY_APP);
        command.setItem(item);
        healthcareServiceDomainService.abolishService(command);
        // 作废号源时同步清空实例定价行。
        syncSalePricingForHealthcareService(id, null, null, null, null, operatorId);
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再启用挂号类型");
        ReactivateHealthcareServiceCommand command = new ReactivateHealthcareServiceCommand();
        command.setOperatorId(operatorId);
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再停用挂号类型");
        RetireHealthcareServiceCommand command = new RetireHealthcareServiceCommand();
        command.setOperatorId(operatorId);
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
     * 列表场景批量解析各号源实例的 SALE 零售价（元），避免 N+1 且须传 {@code instanceIds} 集合。
     *
     * @param services 挂号类型医疗服务列表
     * @return 实例主键 → 销售价（元）
     */
    private Map<Long, BigDecimal> loadSaleAmountMap(List<HealthcareService> services) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (services == null || services.isEmpty()) {
            return result;
        }
        List<Long> serviceIds = new ArrayList<>();
        for (HealthcareService service : services) {
            if (service != null && service.getId() != null) {
                serviceIds.add(service.getId());
            }
        }
        if (serviceIds.isEmpty()) {
            return result;
        }
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        queryCommand.setInstanceIds(serviceIds);
        List<ChargeItemDefinition> priceRows = chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
        for (ChargeItemDefinition row : priceRows) {
            if (row == null || row.getInstanceId() == null || row.getPriceValue() == null) {
                continue;
            }
            if (!PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                continue;
            }
            result.putIfAbsent(row.getInstanceId(), row.getPriceValue());
        }
        return result;
    }

    /**
     * 查询挂在指定医疗服务实例上的销售定价行（{@link PriceType#SALE}）。
     *
     * <p>须走 {@link ChargeItemDefinitionQueryService#findSaleLineForHealthcareServiceRegistration}，
     * 按单实例 {@code instanceId} 解析；禁止误用 {@code findPriceLinesByProjects}（其入参为 {@code instanceIds} 集合）。</p>
     *
     * @param serviceId 医疗服务主键
     * @return 命中的定价行或 {@code null}
     */
    private ChargeItemDefinition findSalePriceLine(Long serviceId) {
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        queryCommand.setInstanceId(serviceId);
        return chargeItemDefinitionQueryService.findSaleLineForHealthcareServiceRegistration(queryCommand);
    }

    /**
     * 同步号源类型（医疗服务实例）挂号费定价：仅写入 {@link PriceType#SALE} 一行，不写采购价/限价。
     *
     * <p>先按 {@code adm_healthcare_service + instanceId} 清空实例下全部定价，再批量落库一条零售价行；
     * 与诊疗/耗材目录共用 {@link ChargePricingCommandAssembler}，金额口径为「元」。</p>
     *
     * @param serviceId     医疗服务主键
     * @param typeCode      财务类型编码（写入定价 {@code type_code}，号源须为诊察费 1012）
     * @param ybType        医保类别（写入定价 {@code yb_type}，可空）
     * @param chargeName    收费项展示名称（写入定价 {@code charge_name}，可空）
     * @param salePriceYuan 销售价（元）；{@code null} 表示仅清空不写回
     */
    private void syncSalePricingForHealthcareService(
        Long serviceId,
        String typeCode,
        String ybType,
        String chargeName,
        BigDecimal salePriceYuan,
        Long operatorId) {
        // 步骤一：移除该号源实例下全部旧定价，避免多 priceType 行叠加。
        RemovePricingByInstanceCommand removeCommand = new RemovePricingByInstanceCommand();
        removeCommand.setOperatorId(operatorId);
        removeCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        removeCommand.setInstanceId(serviceId);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCommand);

        if (salePriceYuan == null) {
            return;
        }

        // 步骤二：号源挂号费仅落 SALE 零售价（禁止 PURCHASE/LIMIT 等多价型）。
        List<SaveAllChargeItemDefinitionsCommand.Item> items = new ArrayList<>();
        ChargePricingCommandAssembler.appendPriceLineIfPresent(
            items,
            TableNameConstants.ADM_HEALTHCARE_SERVICE,
            serviceId,
            typeCode,
            ybType,
            PriceType.SALE.getCode(),
            salePriceYuan,
            chargeName);
        if (items.isEmpty()) {
            return;
        }

        // 步骤三：批量写入 adm_charge_item_definition（集合落库，符合批量 I/O 门禁）。
        SaveAllChargeItemDefinitionsCommand saveCommand = new SaveAllChargeItemDefinitionsCommand();
        saveCommand.setOperatorId(operatorId);
        saveCommand.setItems(items);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(saveCommand);
    }

}
