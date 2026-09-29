/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.service;

import com.openhis.health.application.basic.registrationtype.dto.ConsultationActivityOptionDto;
import com.openhis.health.application.basic.registrationtype.dto.RegistrationTypeDto;
import com.openhis.health.application.basic.registrationtype.mapper.RegistrationTypeMapper;
import com.openhis.health.application.basic.registrationtype.request.CreateRegistrationTypeRequest;
import com.openhis.health.application.basic.registrationtype.request.ListConsultationActivityOptionsRequest;
import com.openhis.health.application.basic.registrationtype.request.UpdateRegistrationTypeRequest;
import com.openhis.health.application.shared.healthcareservice.HealthcareServiceActivityBindingSupport;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.healthcareservice.command.AbolishHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ModifyHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.PublishHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ReactivateHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.RetireHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceDomainService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.HealthcareServiceStatus;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 基础数据「挂号类型」应用服务（AS-BAS-MST-015）。
 *
 * <p>号源经 {@code activity_ids} 绑定诊查费 AD；列表金额只读 enrich AD 当前 SALE；
 * <b>禁止</b>自定义金额、业务写 {@code yb_no}、以及为 HCS 实例同步 SALE（BR-BAS-REG-004）。</p>
 */
@Service
@RequiredArgsConstructor
public class RegistrationTypeAppService {
    /** 作废服务项原因文案（写入领域命令）。 */
    private static final String REASON_DELETE_BY_APP = "应用层删除挂号类型";
    /** 停用服务项原因文案。 */
    private static final String REASON_DEACTIVATE_BY_APP = "应用层停用挂号类型";
    /** 诊查费候选项分页上限（维护弹窗一次加载）。 */
    private static final int CONSULTATION_OPTION_PAGE_SIZE = 500;

    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final HealthcareServiceDomainService healthcareServiceDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final HealthcareServiceActivityBindingSupport activityBindingSupport;
    private final RegistrationTypeMapper registrationTypeMapper;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 列出挂号类型维护台数据，并为每条 enrich 绑定名称与 AD 售价合计（IF-BAS-REG-01）。
     *
     * @return DTO 列表
     */
    public List<RegistrationTypeDto> getRegistrationTypeList() {
        // Gate：只读列表，不写库
        // Pre：拉取维护台可见号源
        List<HealthcareService> all = healthcareServiceQueryService.listServicesForRegistrationManage();
        // Pre：收集全部绑定 AD，一次批量读名称与售价（避免列表 N+1）
        LinkedHashSet<Long> allActivityIds = new LinkedHashSet<>();
        for (HealthcareService entity : all) {
            // 跳过空实体
            if (entity == null) {
                continue;
            }
            allActivityIds.addAll(activityBindingSupport.parseActivityIds(entity.getActivityIds()));
        }
        Map<Long, ActivityDefinition> adById =
            activityBindingSupport.loadActivityMap(new ArrayList<>(allActivityIds));
        Map<Long, BigDecimal> priceById = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, new ArrayList<>(allActivityIds));
        // Asm：投影并按预取价汇总
        List<RegistrationTypeDto> result = new ArrayList<>();
        for (HealthcareService entity : all) {
            if (entity == null) {
                continue;
            }
            result.add(this.toEnrichedDto(entity, adById, priceById));
        }
        return result;
    }

    /**
     * 按主键加载单条挂号类型并 enrich（IF-BAS-REG-02 回显）。
     *
     * @param id 医疗服务主键
     * @return DTO
     * @throws ValidationException id 为空时
     * @throws EntityNotFoundException 记录不存在时
     */
    public RegistrationTypeDto getRegistrationType(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        // Pre：加载医疗服务
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(id);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", id);
        }
        // Asm：enrich 金额与绑定名称
        return this.toEnrichedDto(entity);
    }

    /**
     * 诊查费候选项（ACTIVE + 诊查类目/类型；IF-BAS-REG-02）。
     *
     * @param request 关键字请求；可空对象
     * @return 选项列表（含当前售价）
     */
    public List<ConsultationActivityOptionDto> listConsultationActivityOptions(
        ListConsultationActivityOptionsRequest request) {
        String keyword = null;
        if (request != null) {
            keyword = request.getKeyword();
        }
        // Gate：只读
        // Pre：按类目 53 拉 ACTIVE 诊疗目录页（上限 500）
        ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
        query.setCategoryCode(ServiceRequestCategoryConstants.SERVICE_CATEGORY_CONSULTATION);
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        query.setKeyword(keyword);
        query.setIncludeReferencedPackageChildren(Boolean.FALSE);
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
            query, PageRequest.of(0, CONSULTATION_OPTION_PAGE_SIZE));
        List<Long> ids = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        Map<Long, BigDecimal> priceById = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, ids);
        // Asm：选项 DTO；再并入 type=CONSULTATION 且类目非 53 的 ACTIVE 项（防漏）
        List<ConsultationActivityOptionDto> options = new ArrayList<>();
        LinkedIdSet seen = new LinkedIdSet();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            if (row == null || row.getId() == null || !seen.add(row.getId())) {
                continue;
            }
            options.add(this.toOptionDto(row.getId(), row.getName(), row.getBusNo(), priceById.get(row.getId())));
        }
        this.appendConsultationTypeOptions(keyword, seen, options, priceById);
        return options;
    }

    /**
     * 新增挂号类型：创建并发布为 ACTIVE；只写 {@code activity_ids}（IF-BAS-REG-03）。
     *
     * @param request 创建请求
     * @return enrich 后的 DTO
     * @throws ValidationException 请求非法或绑定不合法时
     */
    public RegistrationTypeDto createRegistrationType(CreateRegistrationTypeRequest request) {
        // Gate：请求体与诊查费绑定口径
        if (request == null) {
            throw new ValidationException("挂号类型请求不能为空");
        }
        activityBindingSupport.assertConsultationBindingsOrEmpty(request.getActivityIds());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再新增挂号类型");
        // Cmd：创建草稿号源并发布
        Long serviceId = healthcareServiceDomainService.createService(
            registrationTypeMapper.toCreateCommand(request, operatorId));
        PublishHealthcareServiceCommand publishCommand = new PublishHealthcareServiceCommand();
        publishCommand.setOperatorId(operatorId);
        PublishHealthcareServiceCommand.Item publishItem = new PublishHealthcareServiceCommand.Item();
        publishItem.setServiceId(serviceId);
        publishCommand.setItem(publishItem);
        healthcareServiceDomainService.publishService(publishCommand);
        // Asm：回读 enrich
        return this.getRegistrationType(serviceId);
    }

    /**
     * 更新挂号类型名称/绑定；保留既有类别类型以免触碰 BR-HCS-008（IF-BAS-REG-04）。
     *
     * @param request 更新请求
     * @return enrich 后的 DTO
     */
    public RegistrationTypeDto updateRegistrationType(UpdateRegistrationTypeRequest request) {
        // Gate：请求体与主键、绑定口径
        if (request == null) {
            throw new ValidationException("挂号类型请求不能为空");
        }
        if (request.getId() == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        activityBindingSupport.assertConsultationBindingsOrEmpty(request.getActivityIds());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再更新挂号类型");
        // Pre：加载实体现值，命令带上原 type/category，避免误改
        HealthcareService existing = healthcareServiceQueryService.loadHealthcareServiceById(request.getId());
        if (existing == null) {
            throw new EntityNotFoundException("挂号类型", request.getId());
        }
        ModifyHealthcareServiceCommand command = registrationTypeMapper.toUpdateCommand(request, operatorId);
        ModifyHealthcareServiceCommand.Item item = command.resolveItems().get(0);
        item.setTypeCode(existing.getTypeCode());
        item.setCategoryCode(existing.getCategoryCode());
        item.setOfferedOrgId(existing.getOfferedOrgId());
        // Cmd：修改绑定与名称
        healthcareServiceDomainService.modifyService(command);
        // Asm：回读 enrich
        return this.getRegistrationType(request.getId());
    }

    /**
     * 作废挂号类型；顺带清理历史 HCS 实例 SALE（若有），不再新建定价（IF-BAS-REG-07）。
     *
     * @param id 医疗服务主键
     */
    public void deleteRegistrationType(Long id) {
        // Gate：主键非空且记录须存在
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(id);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", id);
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再删除挂号类型");
        // Cmd：启用态不可直接废弃，先停用再作废
        if (HealthcareServiceStatus.ACTIVE.getCode().equals(entity.getStatusEnum())) {
            RetireHealthcareServiceCommand retireCommand = new RetireHealthcareServiceCommand();
            retireCommand.setOperatorId(operatorId);
            RetireHealthcareServiceCommand.Item retireItem = new RetireHealthcareServiceCommand.Item();
            retireItem.setServiceId(id);
            retireItem.setReason(REASON_DEACTIVATE_BY_APP);
            retireCommand.setItem(retireItem);
            healthcareServiceDomainService.retireService(retireCommand);
        }
        AbolishHealthcareServiceCommand command = new AbolishHealthcareServiceCommand();
        command.setOperatorId(operatorId);
        AbolishHealthcareServiceCommand.Item item = new AbolishHealthcareServiceCommand.Item();
        item.setServiceId(id);
        item.setReason(REASON_DELETE_BY_APP);
        command.setItem(item);
        healthcareServiceDomainService.abolishService(command);
        // Cmd：清理历史号源实例定价行（旧路径遗留；禁止再写入）
        RemovePricingByInstanceCommand removeCommand = new RemovePricingByInstanceCommand();
        removeCommand.setOperatorId(operatorId);
        removeCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        removeCommand.setInstanceId(id);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCommand);
    }

    /**
     * 重新启用已停用的挂号类型（IF-BAS-REG-05）；启用前再校验绑定。
     *
     * @param id 医疗服务主键
     * @return 最新 DTO
     */
    public RegistrationTypeDto activateRegistrationType(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        HealthcareService entity = healthcareServiceQueryService.loadHealthcareServiceById(id);
        if (entity == null) {
            throw new EntityNotFoundException("挂号类型", id);
        }
        // Gate：启用前绑定仍须合法
        activityBindingSupport.assertConsultationBindingsOrEmpty(entity.getActivityIds());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再启用挂号类型");
        // Cmd：重新启用
        ReactivateHealthcareServiceCommand command = new ReactivateHealthcareServiceCommand();
        command.setOperatorId(operatorId);
        ReactivateHealthcareServiceCommand.Item item = new ReactivateHealthcareServiceCommand.Item();
        item.setServiceId(id);
        command.setItem(item);
        healthcareServiceDomainService.reactivateService(command);
        // Asm：回读
        return this.getRegistrationType(id);
    }

    /**
     * 停用挂号类型（IF-BAS-REG-06）。
     *
     * @param id 医疗服务主键
     * @return 最新 DTO
     */
    public RegistrationTypeDto deactivateRegistrationType(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("挂号类型ID不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "请先登录后再停用挂号类型");
        // Cmd：停用
        RetireHealthcareServiceCommand command = new RetireHealthcareServiceCommand();
        command.setOperatorId(operatorId);
        RetireHealthcareServiceCommand.Item item = new RetireHealthcareServiceCommand.Item();
        item.setServiceId(id);
        item.setReason(REASON_DEACTIVATE_BY_APP);
        command.setItem(item);
        healthcareServiceDomainService.retireService(command);
        // Asm：回读
        return this.getRegistrationType(id);
    }

    /**
     * 实体 → DTO，并 enrich 绑定名称与 AD 售价合计（单条路径自行读价）。
     *
     * @param entity 号源实体
     * @return 已 enrich 的 DTO
     */
    private RegistrationTypeDto toEnrichedDto(HealthcareService entity) {
        List<Long> ids = activityBindingSupport.parseActivityIds(entity.getActivityIds());
        Map<Long, ActivityDefinition> adById = activityBindingSupport.loadActivityMap(ids);
        Map<Long, BigDecimal> priceById = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, ids);
        return this.toEnrichedDto(entity, adById, priceById);
    }

    /**
     * 实体 → DTO，使用调用方预取的 AD 与售价映射（列表批量路径）。
     *
     * @param entity    号源实体
     * @param adById    AD 主键 → 实体
     * @param priceById AD 主键 → 当前 SALE
     * @return 已 enrich 的 DTO
     */
    private RegistrationTypeDto toEnrichedDto(
        HealthcareService entity,
        Map<Long, ActivityDefinition> adById,
        Map<Long, BigDecimal> priceById) {
        RegistrationTypeDto dto = registrationTypeMapper.toDto(entity);
        List<Long> ids = activityBindingSupport.parseActivityIds(entity.getActivityIds());
        StringBuilder names = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            ActivityDefinition ad = adById.get(id);
            // 有名称才拼入摘要
            if (ad != null && ad.getName() != null) {
                if (names.length() > 0) {
                    names.append(',');
                }
                names.append(ad.getName());
            }
            BigDecimal price = priceById.get(id);
            // 缺价按 0
            if (price != null) {
                total = total.add(price);
            }
        }
        dto.setActivityNames(names.toString());
        dto.setAmount(total);
        return dto;
    }

    /**
     * 补充 type=CONSULTATION 且可能不在类目 53 页结果中的 ACTIVE 项。
     *
     * @param keyword    关键字
     * @param seen       已加入的 id
     * @param options    输出列表
     * @param priceById  已有价表（可再扩）
     */
    private void appendConsultationTypeOptions(
        String keyword,
        LinkedIdSet seen,
        List<ConsultationActivityOptionDto> options,
        Map<Long, BigDecimal> priceById) {
        ActivityDefinitionPageQuery typeQuery = new ActivityDefinitionPageQuery();
        typeQuery.setTypeEnum(ActivityType.CONSULTATION.getCode());
        typeQuery.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        typeQuery.setKeyword(keyword);
        typeQuery.setIncludeReferencedPackageChildren(Boolean.FALSE);
        Page<ActivityDefinitionPageQueryModel> typePage = activityDefinitionQueryService.pageServiceCatalog(
            typeQuery, PageRequest.of(0, CONSULTATION_OPTION_PAGE_SIZE));
        List<Long> extraIds = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : typePage.getContent()) {
            if (row != null && row.getId() != null && !seen.contains(row.getId())) {
                extraIds.add(row.getId());
            }
        }
        Map<Long, BigDecimal> extraPrices = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, extraIds);
        for (ActivityDefinitionPageQueryModel row : typePage.getContent()) {
            if (row == null || row.getId() == null || !seen.add(row.getId())) {
                continue;
            }
            BigDecimal price = priceById.get(row.getId());
            if (price == null) {
                price = extraPrices.get(row.getId());
            }
            options.add(this.toOptionDto(row.getId(), row.getName(), row.getBusNo(), price));
        }
    }

    /**
     * 装配诊查费选项行。
     *
     * @param id        AD 主键
     * @param name      名称
     * @param busNo     业务编码
     * @param salePrice 售价
     * @return 选项 DTO
     */
    private ConsultationActivityOptionDto toOptionDto(Long id, String name, String busNo, BigDecimal salePrice) {
        ConsultationActivityOptionDto dto = new ConsultationActivityOptionDto();
        dto.setId(id);
        dto.setName(name);
        dto.setBusNo(busNo);
        dto.setSalePrice(salePrice);
        return dto;
    }

    /**
     * 有序去重集合：记录已加入的 AD 主键。
     */
    private static final class LinkedIdSet {
        private final java.util.LinkedHashSet<Long> delegate = new java.util.LinkedHashSet<>();

        /**
         * @param id 主键
         * @return 首次加入为 true
         */
        boolean add(Long id) {
            return delegate.add(id);
        }

        /**
         * @param id 主键
         * @return 是否已包含
         */
        boolean contains(Long id) {
            return delegate.contains(id);
        }
    }
}
