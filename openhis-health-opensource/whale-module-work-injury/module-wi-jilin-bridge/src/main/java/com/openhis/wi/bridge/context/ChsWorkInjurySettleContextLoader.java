/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.context;

import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.share.WorkInjuryConstants;
import com.openhis.wi.domain.settle.command.WiSettleChargeLine;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.loader.WorkInjurySettleContextLoader;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.wi.gateway.scheme.shared.model.WiPlatformConstants;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 从 HIS 主数据组装 {@link WiSettleContext}（bridge 实现 {@link WorkInjurySettleContextLoader}）。
 *
 * <p>读侧：{@code EncounterContextQueryService}、{@code ProductDefinitionQueryService} 等 health-domain 服务；
 * 不写 {@code health-module-support}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class ChsWorkInjurySettleContextLoader implements WorkInjurySettleContextLoader {

    private final EncounterContextQueryService encounterContextQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 加载 OP_SETTLE 上下文（含 chargeItemIds → 2204 明细）。
     *
     * @param encounterId    就诊 ID
     * @param contractId     费用性质 contract
     * @param paymentDraftId 收银草稿 ID
     * @param chargeItemIds  待结算收费项
     * @return 2201～2207 报文组装用上下文
     */
    @Override
    public WiSettleContext loadSettleContext(
            Long encounterId,
            Long contractId,
            Long paymentDraftId,
            List<Long> chargeItemIds) {
        if (encounterId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 chargeItemIds");
        }
        List<ChargeItem> chargeItems = loadChargeItems(chargeItemIds);
        WiSettleContext ctx = buildBaseContext(encounterId, contractId, paymentDraftId);
        fillEncounterFields(ctx, encounterId);
        mergeAnchor(ctx, encounterId);
        fillChargeLines(ctx, chargeItems);
        return ctx;
    }

    /**
     * 加载登记链上下文（9102/2301；无收费明细）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  费用性质 contract
     * @return 登记/9102 报文组装用上下文
     */
    @Override
    public WiSettleContext loadRegisterContext(Long encounterId, Long contractId) {
        if (encounterId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        WiSettleContext ctx = buildBaseContext(encounterId, contractId, null);
        fillEncounterFields(ctx, encounterId);
        mergeAnchor(ctx, encounterId);
        return ctx;
    }

    /** 初始化 WiSettleContext 公共字段（tenant / operator / medType）。 */
    private WiSettleContext buildBaseContext(Long encounterId, Long contractId, Long paymentDraftId) {
        Long operatorId = currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        WiSettleContext ctx = new WiSettleContext();
        ctx.setEncounterId(encounterId);
        ctx.setContractId(contractId);
        ctx.setPaymentDraftId(paymentDraftId);
        ctx.setMedType(WiPlatformConstants.DEFAULT_MED_TYPE);
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        return ctx;
    }

    /**
     * 从 HIS 主数据填充就诊、科室、医生与主诊断（无收费明细）。
     *
     * @param ctx         待填充上下文
     * @param encounterId 就诊 id
     */
    private void fillEncounterFields(WiSettleContext ctx, Long encounterId) {
        OutpatientEncounterContextQueryModel encounter = encounterContextQueryService
                .mustGetOutpatientContextByEncounterId(encounterId);
        if (isNotEmpty(encounter.getEncounterBusNo())) {
            ctx.setIptOtpNo(encounter.getEncounterBusNo().trim());
        }
        ctx.setAdmTime(encounter.getStartTime());
        if (isNotEmpty(encounter.getPhone())) {
            ctx.setPatientTel(encounter.getPhone().trim());
        }

        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getDoctorId());
        }
        if (organization != null) {
            ctx.setDeptCode(organization.getYbNo());
            ctx.setDeptName(organization.getName());
        }
        if (doctor != null) {
            ctx.setAtddrNo(doctor.getYbNo());
            ctx.setDrName(doctor.getName());
        }

        InsuranceDiagnosisCodeSupport.MainDiagnosis diagnosis = InsuranceDiagnosisCodeSupport.resolveMainDiagnosis(
                encounterId,
                encounterQueryService,
                conditionDefinitionQueryService);
        ctx.setAdmDiagCode(diagnosis.code());
        ctx.setAdmDiagName(diagnosis.name());
    }

    /**
     * 将 anchor 中 psn_no / qualification_id 等合并进上下文（anchor 优先于 HIS 缺省）。
     *
     * @param ctx         上下文
     * @param encounterId 就诊 id
     */
    private void mergeAnchor(WiSettleContext ctx, Long encounterId) {
        Optional<YbWiEncounterAnchor> anchorOpt = encounterAnchorRepository.findByEncounterId(encounterId);
        if (anchorOpt.isEmpty()) {
            return;
        }
        YbWiEncounterAnchor anchor = anchorOpt.get();
        if (isNotEmpty(anchor.getPsnNo())) {
            ctx.setPsnNo(anchor.getPsnNo().trim());
        }
        if (isNotEmpty(anchor.getQualificationId())) {
            ctx.setQualificationId(anchor.getQualificationId().trim());
        }
        if (isNotEmpty(anchor.getInjureAffirmId())) {
            ctx.setInjureAffirmId(anchor.getInjureAffirmId().trim());
        }
        if (isNotEmpty(anchor.getIptOtpNo())) {
            ctx.setIptOtpNo(anchor.getIptOtpNo().trim());
        }
        if (isNotEmpty(anchor.getAdmDiagCode())) {
            ctx.setAdmDiagCode(anchor.getAdmDiagCode().trim());
        }
        if (isNotEmpty(anchor.getMedType())) {
            ctx.setMedType(WorkInjuryConstants.normalizeMedType(anchor.getMedType()));
        }
    }

    /**
     * 收费项 → 2204 明细行，并汇总 medfeeSumamt。
     *
     * @param ctx         上下文（写入 chargeLines / medfeeSumamt）
     * @param chargeItems HIS 收费项
     */
    private void fillChargeLines(WiSettleContext ctx, List<ChargeItem> chargeItems) {
        Map<Long, ChargeItemDefinition> definitionMap = buildDefinitionMap(chargeItems);
        Map<String, ProductDefinitionCatalogQueryModel> productMap = loadProducts(chargeItems, definitionMap);
        List<WiSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem item : chargeItems) {
            ProductDefinitionCatalogQueryModel product = resolveProduct(item, productMap, definitionMap);
            if (product == null || isEmpty(product.getYbNo())) {
                String displayName =
                    product != null && isNotEmpty(product.getProductName())
                        ? product.getProductName().trim()
                        : null;
                StringBuilder tip = new StringBuilder("收费项缺少国家医保编码(yb_no)");
                if (isNotEmpty(displayName)) {
                    tip.append("：").append(displayName);
                }
                if (isNotEmpty(item.getBusNo())) {
                    tip.append("，单据号=").append(item.getBusNo().trim());
                }
                if (product != null && isNotEmpty(product.getProductBusNo())) {
                    tip.append("，院内产品编码=").append(product.getProductBusNo().trim());
                }
                tip.append("，chargeItemId=").append(item.getId());
                throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, tip.toString());
            }
            WiSettleChargeLine line = new WiSettleChargeLine();
            line.setChargeItemId(item.getId());
            if (isNotEmpty(item.getBusNo())) {
                line.setFeedetlSn(item.getBusNo().trim());
            } else {
                line.setFeedetlSn(String.valueOf(item.getId()));
            }
            line.setRxno(item.getPrescriptionNo());
            line.setMedListCodg(product.getYbNo().trim());
            line.setPric(item.getUnitPrice());
            line.setCnt(item.getQuantityValue());
            line.setUmamt(resolveUmamt(item));
            line.setFeeOcurTime(item.getOccurrenceTime());
            line.setListType(resolveListType(item, definitionMap.get(item.getDefinitionId())));
            if (isNotEmpty(ctx.getAtddrNo())) {
                line.setBilgDrCodg(ctx.getAtddrNo());
            }
            if (isNotEmpty(ctx.getDeptCode())) {
                line.setBilgDeptCodg(ctx.getDeptCode());
            }
            lines.add(line);
            if (line.getUmamt() != null) {
                total = total.add(line.getUmamt());
            }
        }
        ctx.setChargeLines(lines);
        ctx.setMedfeeSumamt(total);
    }

    /** 批量加载收费项并校验 id 完整性。 */
    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }
        return chargeItems;
    }

    /** 2204 umamt：优先 totalPrice，否则单价×数量（两位小数）。 */
    private BigDecimal resolveUmamt(ChargeItem item) {
        if (item.getTotalPrice() != null) {
            return item.getTotalPrice().setScale(2, RoundingMode.HALF_UP);
        }
        if (item.getUnitPrice() != null && item.getQuantityValue() != null) {
            return item.getUnitPrice().multiply(item.getQuantityValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** 按 productTable 推断 2204 list_type（手册 1药品/2诊疗/3材料；勿用省医保 101/201/103）。 */
    private String resolveListType(ChargeItem item, ChargeItemDefinition definition) {
        String productTable = item.getProductTable();
        if (isEmpty(productTable) && definition != null) {
            productTable = definition.getInstanceTable();
        }
        if (isEmpty(productTable)) {
            return WiPlatformConstants.DEFAULT_LIST_TYPE;
        }
        String table = productTable.trim().toLowerCase();
        if (table.contains("medication") || table.contains("drug")) {
            return WiPlatformConstants.LIST_TYPE_DRUG;
        }
        if (table.contains("device") || table.contains("material")) {
            return WiPlatformConstants.LIST_TYPE_MATERIAL;
        }
        return WiPlatformConstants.DEFAULT_LIST_TYPE;
    }

    /** chargeItem.definitionId → ChargeItemDefinition 批量映射。 */
    private Map<Long, ChargeItemDefinition> buildDefinitionMap(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /** 收费项关联目录 batchLoad → compositeKey Map（取 yb_no）。 */
    private Map<String, ProductDefinitionCatalogQueryModel> loadProducts(
            List<ChargeItem> chargeItems,
            Map<Long, ChargeItemDefinition> definitionMap) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem item : chargeItems) {
            ChargeItemDefinition definition = null;
            if (item.getDefinitionId() != null) {
                definition = definitionMap.get(item.getDefinitionId());
            }
            ProductDefinitionBatchLookup lookup = resolveCatalogLookup(item, definition);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionQueryService.batchLoadAsMap(lookups);
    }

    /** 从 chargeItem 或 definition 解析 ProductDefinition 批量查询键。 */
    private ProductDefinitionBatchLookup resolveCatalogLookup(ChargeItem row, ChargeItemDefinition definition) {
        if (row.getProductId() != null && isNotEmpty(row.getProductTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(row.getProductId());
            lookup.setProductTableName(row.getProductTable().trim());
            return lookup;
        }
        if (definition != null && definition.getInstanceId() != null && isNotEmpty(definition.getInstanceTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(definition.getInstanceId());
            lookup.setProductTableName(definition.getInstanceTable().trim());
            return lookup;
        }
        return null;
    }

    /** 单条 chargeItem 在 productMap 中取目录项（med_list_codg 来源）。 */
    private ProductDefinitionCatalogQueryModel resolveProduct(
            ChargeItem item,
            Map<String, ProductDefinitionCatalogQueryModel> productMap,
            Map<Long, ChargeItemDefinition> definitionMap) {
        ChargeItemDefinition definition = null;
        if (item.getDefinitionId() != null) {
            definition = definitionMap.get(item.getDefinitionId());
        }
        ProductDefinitionBatchLookup lookup = resolveCatalogLookup(item, definition);
        if (lookup == null) {
            return null;
        }
        return productMap.get(ProductDefinitionKeyUtil.buildCompositeKey(
                lookup.getProductTableName(), lookup.getProductDefinitionId()));
    }
}
