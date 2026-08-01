/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.support;

import com.openhis.health.application.shared.advice.OrderChargeDraftAssembler;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检收费懒生成：为 PE 就诊补齐 PLANNED 收费明细（GAP-PE-CHG-02）。
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamPlannedChargeEnsureSupport {

    private final EncounterQueryService encounterQueryService;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 单就诊幂等补齐 PLANNED 收费项。
     *
     * @param encounterId 就诊主键
     * @param operatorId  操作人
     */
    public void ensureForEncounter(Long encounterId, Long operatorId) {
        if (encounterId == null || operatorId == null) {
            return;
        }
        // Gate：须为 PE 就诊
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "非体检就诊不可收费");
        Long selfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (selfPayAccountId == null) {
            // Cmd：幂等开立个人现金账户，供后续收费项挂账
            OpenEncounterAccountsCommand openCmd = new OpenEncounterAccountsCommand();
            openCmd.setOperatorId(operatorId);
            OpenEncounterAccountsCommand.Item item = new OpenEncounterAccountsCommand.Item();
            item.setEncounterId(encounterId);
            item.setPatientId(encounter.getPatientId());
            item.setDefaultFlag(Boolean.TRUE);
            item.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
            openCmd.setItem(item);
            accountDomainService.openEncounterAccounts(openCmd);
            selfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        }
        if (selfPayAccountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING + ": 无法开立自费账户");
        }
        List<ServiceRequestListModel> serviceRequests = serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Set<Long> existingServiceIds = this.loadExistingServiceRequestChargeAnchors(encounterId);
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (RequestStatus.CANCELLED.getCode().equals(serviceRequest.getStatusEnum())) {
                continue;
            }
            if (existingServiceIds.contains(serviceRequest.getId())) {
                continue;
            }
            this.createPlannedChargeForServiceRequest(
                encounter,
                serviceRequest,
                selfPayAccountId,
                operatorId);
            existingServiceIds.add(serviceRequest.getId());
        }
    }

    /**
     * 批量幂等补齐 PLANNED 收费项。
     *
     * @param encounterIds 就诊主键集合
     * @param operatorId   操作人
     */
    public void ensureForEncounters(Collection<Long> encounterIds, Long operatorId) {
        if (encounterIds == null || encounterIds.isEmpty() || operatorId == null) {
            return;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinct.add(encounterId);
            }
        }
        for (Long encounterId : distinct) {
            this.ensureForEncounter(encounterId, operatorId);
        }
    }

    /**
     * 加载就诊下已存在 wkf_service_request 锚点的 serviceId 集合。
     *
     * @param encounterId 就诊主键
     * @return 已有锚点 serviceId
     */
    private Set<Long> loadExistingServiceRequestChargeAnchors(Long encounterId) {
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        Set<Long> serviceIds = new HashSet<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getServiceId() == null) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())) {
                serviceIds.add(row.getServiceId());
            }
        }
        return serviceIds;
    }

    /**
     * 为单个 ServiceRequest 创建 PLANNED 收费项。
     *
     * @param encounter        就诊详情
     * @param serviceRequest   服务申请
     * @param selfPayAccountId 自费账户
     * @param operatorId       操作人
     */
    private void createPlannedChargeForServiceRequest(
        EncounterDetailQueryModel encounter,
        ServiceRequestListModel serviceRequest,
        Long selfPayAccountId,
        Long operatorId) {
        Long activityId = serviceRequest.getActivityId();
        if (activityId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENSURE_PRICE_MISSING + ": 体检项目缺少活动定义");
        }
        // Pre：按活动定义主键取产品读模型与 SALE 定价行（与医生站诊疗挂费同口径）
        ProductDefinitionDisplayDto product = this.loadActivityProduct(activityId);
        Long orgId = serviceRequest.getOrgId();
        if (orgId == null) {
            orgId = encounter.getOrganizationId();
        }
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            activityId,
            product,
            orgId,
            encounter.getId(),
            null,
            null);
        if (priceLine == null) {
            ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityId);
            String activityName = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
            if (detail != null && detail.getBaseInfo() != null && StringUtils.isNotEmpty(detail.getBaseInfo().getName())) {
                activityName = detail.getBaseInfo().getName();
            }
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENSURE_PRICE_MISSING + ": 项目「" + activityName + "」缺少零售价定价");
        }
        BigDecimal quantity = serviceRequest.getQuantity();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        String quantityUnit = serviceRequest.getUnitCode();
        CreateChargeItemCommand.Item draftItem = OrderChargeDraftAssembler.toCreateChargeItemDraftItemFromOrder(
            encounter.getId(),
            encounter.getPatientId(),
            selfPayAccountId,
            orgId,
            serviceRequest.getId(),
            TableNameConstants.WKF_SERVICE_REQUEST,
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            activityId,
            priceLine,
            quantity,
            quantityUnit,
            product);
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(createCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounter.getId(),
            TableNameConstants.WKF_SERVICE_REQUEST,
            serviceRequest.getId());
    }

    /**
     * 加载诊疗活动产品读模型。
     *
     * @param activityId 活动定义主键
     * @return 产品读模型
     */
    private ProductDefinitionDisplayDto loadActivityProduct(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionDisplayDto product = map.get(key);
        if (product == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENSURE_PRICE_MISSING + ": 未找到体检项目定义");
        }
        return product;
    }
}
