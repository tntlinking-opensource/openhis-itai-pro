/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.mapper;

import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogChildDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogDetailDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogInitDto;
import com.openhis.health.application.basic.servicecatalog.request.CreateServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.UpdateServiceCatalogRequest;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link ServiceCatalogMapper} 默认实现：读模型/实体到 DTO 的字段拷贝与 0/1 标志、枚举字符串转整型。
 */
@Component
@RequiredArgsConstructor
public class ServiceCatalogMapperImpl implements ServiceCatalogMapper {

    /** 术语读侧：初始化页医保等级等下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 将活动定义实体完整映射为详情 DTO，布尔域统一转为 0/1 整型便于前端表单绑定。
     *
     * @param activityDefinition 领域实体
     * @return 详情 DTO；入参 {@code null} 时返回 {@code null}
     */
    @Override
    public ServiceCatalogDetailDto converterToServiceCatalogDetailDto(ActivityDefinition activityDefinition) {
        if (activityDefinition == null) {
            return null;
        }

        ServiceCatalogDetailDto detailDto = new ServiceCatalogDetailDto();
        detailDto.setId(activityDefinition.getId());
        detailDto.setCategoryCode(activityDefinition.getCategoryCode());
        detailDto.setBusNo(activityDefinition.getBusNo());
        detailDto.setName(activityDefinition.getName());
        detailDto.setPyStr(activityDefinition.getPyStr());
        detailDto.setWbStr(activityDefinition.getWbStr());
        detailDto.setPackageUnitCode(activityDefinition.getPackageUnitCode());
        detailDto.setActivityVersion(activityDefinition.getActivityVersion());
        detailDto.setMerchandiseName(activityDefinition.getMerchandiseName());
        detailDto.setMerchandisePyStr(activityDefinition.getMerchandisePyStr());
        detailDto.setMerchandiseWbStr(activityDefinition.getMerchandiseWbStr());
        detailDto.setDefEncounterUnitCode(activityDefinition.getDefEncounterUnitCode());
        detailDto.setDefInhospitalUnitCode(activityDefinition.getDefInhospitalUnitCode());
        if (Boolean.TRUE.equals(activityDefinition.getYbFlag())) {
            detailDto.setYbFlag(1);
        } else {
            detailDto.setYbFlag(0);
        }
        if (Boolean.TRUE.equals(activityDefinition.getPricingFlag())) {
            detailDto.setPricingFlag(1);
        } else {
            detailDto.setPricingFlag(0);
        }
        detailDto.setYbNo(activityDefinition.getYbNo());
        if (Boolean.TRUE.equals(activityDefinition.getYbMatchFlag())) {
            detailDto.setYbMatchFlag(1);
        } else {
            detailDto.setYbMatchFlag(0);
        }
        detailDto.setStatusEnum(
            EnumUtil.toDisplayIntegerFromCodeOrNumber(PublicationStatus.class, activityDefinition.getStatusEnum()));
        detailDto.setBodySiteCode(activityDefinition.getBodySiteCode());
        detailDto.setSpecimenCode(activityDefinition.getSpecimenCode());
        detailDto.setDescriptionText(activityDefinition.getDescriptionText());
        if (activityDefinition.getRuleId() == null) {
            detailDto.setRuleId(null);
        } else {
            detailDto.setRuleId(activityDefinition.getRuleId().longValue());
        }
        detailDto.setTypeEnum(
            EnumUtil.toDisplayIntegerFromCodeOrNumber(ActivityType.class, activityDefinition.getTypeEnum()));
        detailDto.setOrgId(activityDefinition.getOrgId());
        detailDto.setLocationId(activityDefinition.getLocationId());
        detailDto.setChrgitmLv(activityDefinition.getChrgitmLv());
        detailDto.setMrSettlementTypeCode(activityDefinition.getMrSettlementTypeCode());
        // 子项标记：与主表 children_flag 一致（BR-ACTDEF-006），便于实体直出详情时识别组套
        if (Boolean.TRUE.equals(activityDefinition.getChildrenFlag())) {
            detailDto.setChildrenFlag(1);
        } else {
            detailDto.setChildrenFlag(0);
        }
        return detailDto;
    }

    /**
     * 将创建请求压平为单条 {@link CreateActivityDefinitionCommand}，子项列表映射为领域子命令并设置 childrenFlag。
     *
     * @param request 创建请求
     * @return 领域创建命令；请求 {@code null} 时返回 {@code null}
     */
    @Override
    public CreateActivityDefinitionCommand converterToCreateParam(CreateServiceCatalogRequest request) {
        if (request == null) {
            return null;
        }
        CreateActivityDefinitionCommand command = new CreateActivityDefinitionCommand();
        CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand item =
            new CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand();
        item.setCategoryCode(request.getCategoryCode());
        item.setBusNo(request.getBusNo());
        item.setName(request.getName());
        item.setPackageUnitCode(request.getPackageUnitCode());
        item.setActivityVersion(request.getActivityVersion());
        item.setMerchandiseName(request.getMerchandiseName());
        item.setDefEncounterUnitCode(request.getDefEncounterUnitCode());
        item.setDefInhospitalUnitCode(request.getDefInhospitalUnitCode());
        if (request.getTypeEnum() != null) {
            item.setTypeEnum(String.valueOf(request.getTypeEnum()));
        }
        item.setDescriptionText(request.getDescriptionText());
        item.setBodySiteCode(request.getBodySiteCode());
        item.setSpecimenCode(request.getSpecimenCode());
        item.setOrgId(request.getOrgId());
        item.setLocationId(request.getLocationId());
        item.setRuleId(request.getRuleId());
        if (request.getPricingFlag() != null) {
            item.setPricingFlag(request.getPricingFlag() == 1);
        }
        if (request.getYbFlag() != null) {
            item.setYbFlag(request.getYbFlag() == 1);
        }
        item.setYbNo(request.getYbNo());
        if (request.getYbMatchFlag() != null) {
            item.setYbMatchFlag(request.getYbMatchFlag() == 1);
        }
        item.setChrgitmLv(request.getChrgitmLv());
        item.setMrSettlementTypeCode(request.getMrSettlementTypeCode());
        List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> children = new ArrayList<>();
        if (request.getChildren() != null) {
            // 子项逐条转为领域子命令，由领域决定是否落子表
            for (ServiceCatalogChildDto child : request.getChildren()) {
                CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand childItem =
                    new CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand();
                childItem.setChildrenId(child.getChildrenId());
                childItem.setChildrenNum(child.getChildrenNum());
                childItem.setChildrenName(child.getChildrenName());
                children.add(childItem);
            }
        }
        item.setChildren(children);
        item.setChildrenFlag(!children.isEmpty());
        command.setItem(item);
        return command;
    }

    /**
     * 将更新请求装配为单条 {@link UpdateActivityDefinitionCommand}，子项列表与创建路径对称处理。
     *
     * @param request 更新请求
     * @return 领域更新命令；请求 {@code null} 时返回 {@code null}
     */
    @Override
    public UpdateActivityDefinitionCommand converterToUpdateParam(UpdateServiceCatalogRequest request) {
        if (request == null) {
            return null;
        }
        UpdateActivityDefinitionCommand param = new UpdateActivityDefinitionCommand();
        UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand item =
            new UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand();
        item.setId(request.getId());
        item.setCategoryCode(request.getCategoryCode());
        item.setName(request.getName());
        if (request.getTypeEnum() != null) {
            item.setTypeEnum(String.valueOf(request.getTypeEnum()));
        }
        item.setPackageUnitCode(request.getPackageUnitCode());
        item.setActivityVersion(request.getActivityVersion());
        item.setMerchandiseName(request.getMerchandiseName());
        item.setDefEncounterUnitCode(request.getDefEncounterUnitCode());
        item.setDefInhospitalUnitCode(request.getDefInhospitalUnitCode());
        if (request.getYbFlag() != null) {
            item.setYbFlag(request.getYbFlag() == 1);
        }
        if (request.getPricingFlag() != null) {
            item.setPricingFlag(request.getPricingFlag() == 1);
        }
        item.setYbNo(request.getYbNo());
        if (request.getYbMatchFlag() != null) {
            item.setYbMatchFlag(request.getYbMatchFlag() == 1);
        }
        item.setBodySiteCode(request.getBodySiteCode());
        item.setSpecimenCode(request.getSpecimenCode());
        item.setDescriptionText(request.getDescriptionText());
        item.setRuleId(request.getRuleId());
        item.setOrgId(request.getOrgId());
        item.setLocationId(request.getLocationId());
        item.setChrgitmLv(request.getChrgitmLv());
        item.setMrSettlementTypeCode(request.getMrSettlementTypeCode());
        List<UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> children = new ArrayList<>();
        if (request.getChildren() != null) {
            for (ServiceCatalogChildDto child : request.getChildren()) {
                UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand childItem =
                    new UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand();
                childItem.setChildrenId(child.getChildrenId());
                childItem.setChildrenNum(child.getChildrenNum());
                childItem.setChildrenName(child.getChildrenName());
                children.add(childItem);
            }
        }
        item.setChildren(children);
        item.setChildrenFlag(!children.isEmpty());
        param.setItem(item);
        return param;
    }

    /**
     * 返回初始化 DTO 中依赖术语服务的部分（当前为收费项目医保等级选项）。
     *
     * @return 含 chrgitmLvOptions 的初始化对象
     */
    @Override
    public ServiceCatalogInitDto getInit() {
        ServiceCatalogInitDto serviceCatalogInitDto = new ServiceCatalogInitDto();
        List<OptionDto> chrgitmLvOptions = codeSystemQueryService.getOptionListByCodeSystemName("chrgitm_lv");
        serviceCatalogInitDto.setChrgitmLvOptions(chrgitmLvOptions);
        return serviceCatalogInitDto;
    }

}
