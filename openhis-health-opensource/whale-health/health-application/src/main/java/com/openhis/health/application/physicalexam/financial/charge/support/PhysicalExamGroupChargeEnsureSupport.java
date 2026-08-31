/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.support;

import com.openhis.health.application.peis.group.support.PeisGroupContractChargePriceResolver;
import com.openhis.health.application.peis.group.support.PeisGroupPackageChargeDraftAssembler;
import com.openhis.health.application.peis.history.support.PeisPackageSnapshotSupport;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisGroupContractConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.advice.OrderChargeDraftAssembler;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 团检收费懒生成：套餐内子项不单独挂费，仅生成一条团检套餐 ChargeItem（锚定团检合同）；加项仍按单项挂费。
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamGroupChargeEnsureSupport {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final PeisGroupContractChargePriceResolver peisGroupContractChargePriceResolver;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    public List<String> ensureForGroupEncounter(
            EncounterDetailQueryModel encounter,
            PeisEncounterExtModel ext,
            Long selfPayAccountId,
            Long operatorId,
            List<ServiceRequestListModel> serviceRequests) {
        List<String> warnings = new ArrayList<>();
        Set<Long> baselineActivityIds = new HashSet<>(
            PeisPackageSnapshotSupport.extractChildActivityIds(ext.getPackageSnapshotJson()));
        Long packageActivityId = ext.getPackageActivityId();
        if (baselineActivityIds.isEmpty() && packageActivityId != null) {
            List<ActivityDefinitionChildQueryModel> children =
                activityDefinitionQueryService.listChildrenByActivityId(packageActivityId);
            for (ActivityDefinitionChildQueryModel child : children) {
                if (child != null && child.getChildrenId() != null) {
                    baselineActivityIds.add(child.getChildrenId());
                }
            }
        }
        boolean packageChargeExempt = this.isPackageChargeExempt(ext);
        this.voidBaselineServiceRequestCharges(encounter.getId(), serviceRequests, baselineActivityIds, operatorId);
        Set<Long> existingServiceIds = this.loadExistingServiceRequestChargeAnchors(encounter.getId());
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (RequestStatus.CANCELLED.getCode().equals(serviceRequest.getStatusEnum())) {
                continue;
            }
            Long activityId = serviceRequest.getActivityId();
            if (activityId != null && baselineActivityIds.contains(activityId)) {
                continue;
            }
            if (existingServiceIds.contains(serviceRequest.getId())) {
                continue;
            }
            String warning = this.createPlannedChargeForServiceRequest(
                encounter, serviceRequest, selfPayAccountId, operatorId);
            if (StringUtils.isNotEmpty(warning)) {
                warnings.add(warning);
            } else {
                existingServiceIds.add(serviceRequest.getId());
            }
        }
        if (!packageChargeExempt) {
            String warning = this.ensureGroupPackageChargeItem(
                encounter, ext, selfPayAccountId, operatorId, packageActivityId);
            if (StringUtils.isNotEmpty(warning)) {
                warnings.add(warning);
            }
        } else {
            this.voidGroupPackageChargeItem(encounter.getId(), ext.getContractId(), operatorId);
        }
        return warnings;
    }

    private boolean isPackageChargeExempt(PeisEncounterExtModel ext) {
        if (ext.getContractId() != null) {
            try {
                PeisGroupContract contract = peisGroupCheckQueryService.requireContract(ext.getContractId());
                if (PeisGroupContractConstants.isPaid(contract.getPayStatusCode())) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 合同缺失时不阻断加项挂费
            }
        }
        Optional<PeisGroupRoster> roster = peisGroupCheckQueryService.findRosterByEncounterId(ext.getEncounterId());
        if (roster.isPresent()) {
            String payStatus = roster.get().getPayStatusCode();
            if (PeisGroupRosterConstants.PAY_ENROLLED.equals(payStatus)
                || PeisGroupRosterConstants.PAY_PAID.equals(payStatus)) {
                return true;
            }
        }
        return false;
    }

    private void voidBaselineServiceRequestCharges(
            Long encounterId,
            List<ServiceRequestListModel> serviceRequests,
            Set<Long> baselineActivityIds,
            Long operatorId) {
        if (encounterId == null || operatorId == null || serviceRequests == null) {
            return;
        }
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            Long activityId = serviceRequest.getActivityId();
            if (activityId == null || !baselineActivityIds.contains(activityId)) {
                continue;
            }
            VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd =
                new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
            cmd.setOperatorId(operatorId);
            cmd.setEncounterId(encounterId);
            cmd.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
            cmd.setServiceId(serviceRequest.getId());
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(cmd);
        }
    }

    private void voidGroupPackageChargeItem(Long encounterId, Long contractId, Long operatorId) {
        if (encounterId == null || operatorId == null) {
            return;
        }
        this.voidPackageChargeAtAnchor(encounterId, TableNameConstants.PEIS_ENCOUNTER_EXT, encounterId, operatorId);
        if (contractId != null) {
            this.voidPackageChargeAtAnchor(
                encounterId, TableNameConstants.PEIS_GROUP_CONTRACT, contractId, operatorId);
        }
    }

    private void voidPackageChargeAtAnchor(
            Long encounterId, String serviceTable, Long serviceId, Long operatorId) {
        chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
            encounterId, serviceTable, serviceId);
        VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd = new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(encounterId);
        cmd.setServiceTable(serviceTable);
        cmd.setServiceId(serviceId);
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(cmd);
    }

    private String ensureGroupPackageChargeItem(
            EncounterDetailQueryModel encounter,
            PeisEncounterExtModel ext,
            Long selfPayAccountId,
            Long operatorId,
            Long packageActivityId) {
        if (packageActivityId == null) {
            return "团检套餐未配置，已跳过挂费";
        }
        Long contractId = ext.getContractId();
        if (contractId == null) {
            return "团检合同未配置，已跳过套餐挂费";
        }
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(packageActivityId);
        ActivityDefinitionBaseQueryModel base = detail == null ? null : detail.getBaseInfo();
        String packageName = PeisPackageSnapshotSupport.extractPackageName(ext.getPackageSnapshotJson());
        if (StringUtils.isEmpty(packageName) && base != null && StringUtils.isNotEmpty(base.getName())) {
            packageName = base.getName();
        }
        if (StringUtils.isEmpty(packageName)) {
            packageName = "团检套餐";
        }
        ProductDefinitionDisplayDto product;
        try {
            product = this.loadActivityProduct(packageActivityId);
        } catch (BusinessException ex) {
            return "团检套餐「" + packageName + "」未找到目录定义，已跳过挂费";
        }
        Long orgId = encounter.getOrganizationId();
        GroupPackagePriceResolution priceResolution = this.resolveGroupPackagePrice(
            contractId, packageActivityId, product, orgId, encounter.getId());
        if (priceResolution == null || priceResolution.effectivePriceLine() == null
            || priceResolution.effectivePriceLine().getPriceValue() == null) {
            return "团检套餐「" + packageName + "」缺少合同金额或目录定价，已跳过挂费";
        }
        if (priceResolution.effectivePriceLine().getId() == null) {
            return "团检套餐「" + packageName + "」缺少收费项目定义(ChargeItemDefinition)，已跳过挂费";
        }
        ChargeItem existing = this.findGroupPackageChargeItem(encounter.getId(), contractId);
        if (existing != null) {
            if (!ChargeItemStatus.PLANNED.getCode().equals(existing.getStatusEnum())) {
                return null;
            }
            if (this.isSamePackageChargeAnchor(existing, contractId)
                && (this.sameMoney(priceResolution.effectivePriceLine().getPriceValue(), existing.getUnitPrice())
                    || this.sameMoney(priceResolution.effectivePriceLine().getPriceValue(), existing.getTotalPrice()))) {
                return null;
            }
            this.voidGroupPackageChargeItem(encounter.getId(), contractId, operatorId);
        } else {
            this.voidGroupPackageChargeItem(encounter.getId(), contractId, operatorId);
        }
        CreateChargeItemCommand.Item draftItem = PeisGroupPackageChargeDraftAssembler.toDraftItem(
            encounter.getId(),
            encounter.getPatientId(),
            selfPayAccountId,
            orgId,
            contractId,
            packageActivityId,
            priceResolution.effectivePriceLine(),
            priceResolution.contractPriceApplied(),
            priceResolution.catalogUnitPrice(),
            product);
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(createCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounter.getId(),
            TableNameConstants.PEIS_GROUP_CONTRACT,
            contractId);
        return null;
    }

    private GroupPackagePriceResolution resolveGroupPackagePrice(
            Long contractId,
            Long packageActivityId,
            ProductDefinitionDisplayDto product,
            Long orgId,
            Long encounterId) {
        ChargeItemDefinition catalogPriceLine = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            packageActivityId,
            product,
            orgId,
            encounterId,
            null,
            null);
        if (catalogPriceLine == null) {
            catalogPriceLine = peisGroupContractChargePriceResolver.resolveCatalogPackagePriceLine(
                packageActivityId, orgId);
        }
        BigDecimal catalogUnitPrice = catalogPriceLine == null ? null : catalogPriceLine.getPriceValue();
        BigDecimal contractUnitPrice = peisGroupContractChargePriceResolver.resolvePackageUnitPrice(
            contractId, packageActivityId, orgId);
        if (contractUnitPrice != null) {
            ChargeItemDefinition effective = peisGroupContractChargePriceResolver.toChargePriceLine(
                contractUnitPrice, catalogPriceLine, orgId);
            return new GroupPackagePriceResolution(effective, true, catalogUnitPrice);
        }
        if (catalogPriceLine == null) {
            return null;
        }
        return new GroupPackagePriceResolution(catalogPriceLine, false, catalogUnitPrice);
    }

    private boolean isSamePackageChargeAnchor(ChargeItem row, Long contractId) {
        if (row == null || contractId == null) {
            return false;
        }
        return TableNameConstants.PEIS_GROUP_CONTRACT.equals(row.getServiceTable())
            && contractId.equals(row.getServiceId());
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        return MonetaryAmountSupport.scaleCalc(left).compareTo(MonetaryAmountSupport.scaleCalc(right)) == 0;
    }

    private ChargeItem findGroupPackageChargeItem(Long encounterId, Long contractId) {
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        ChargeItem legacy = null;
        ChargeItem contractAnchored = null;
        for (ChargeItem row : rows) {
            if (row == null || row.getServiceId() == null) {
                continue;
            }
            if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (TableNameConstants.PEIS_GROUP_CONTRACT.equals(row.getServiceTable())
                && contractId != null
                && contractId.equals(row.getServiceId())) {
                contractAnchored = row;
            }
            if (TableNameConstants.PEIS_ENCOUNTER_EXT.equals(row.getServiceTable())
                && encounterId.equals(row.getServiceId())) {
                legacy = row;
            }
        }
        return contractAnchored != null ? contractAnchored : legacy;
    }

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

    private String createPlannedChargeForServiceRequest(
            EncounterDetailQueryModel encounter,
            ServiceRequestListModel serviceRequest,
            Long selfPayAccountId,
            Long operatorId) {
        Long activityId = serviceRequest.getActivityId();
        if (activityId == null) {
            return "体检加项缺少活动定义，已跳过挂费";
        }
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityId);
        ActivityDefinitionBaseQueryModel base = detail == null ? null : detail.getBaseInfo();
        String activityName = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
        if (base != null && StringUtils.isNotEmpty(base.getName())) {
            activityName = base.getName();
        }
        if (base != null && Boolean.FALSE.equals(base.getPricingFlag())) {
            return null;
        }
        ProductDefinitionDisplayDto product;
        try {
            product = this.loadActivityProduct(activityId);
        } catch (BusinessException ex) {
            return "加项「" + activityName + "」未找到目录定义，已跳过挂费";
        }
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
            return "加项「" + activityName + "」缺少零售价定价，已跳过挂费";
        }
        BigDecimal quantity = serviceRequest.getQuantity();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
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
            serviceRequest.getUnitCode(),
            product);
        draftItem.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(createCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounter.getId(),
            TableNameConstants.WKF_SERVICE_REQUEST,
            serviceRequest.getId());
        return null;
    }

    private ProductDefinitionDisplayDto loadActivityProduct(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        java.util.Map<String, ProductDefinitionDisplayDto> map =
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

    private record GroupPackagePriceResolution(
        ChargeItemDefinition effectivePriceLine,
        boolean contractPriceApplied,
        BigDecimal catalogUnitPrice) {
    }
}
