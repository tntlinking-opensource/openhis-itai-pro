/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.access.InventoryInitializeFillAccess;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.chs.domain.inventory.command.InitializeInventoryCommand;
import com.openhis.chs.domain.inventory.command.InitializeInventoryPairCommand;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.result.InitializeInventoryFillMeta;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 批初始化配对填充 bridge 实现（P14）。
 *
 * <p><b>职责：</b>从 health-domain 库房库存与药品主数据组装 3501+3502 配对请求，支持分页与跳过已初始化行。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.inventory.access.InventoryInitializeFillAccess}</p>
 * <p><b>调用方：</b>{@code InventoryInitializeWorkflow}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryInitializeUploadContextLoader implements InventoryInitializeFillAccess {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadQueryService invUploadQueryService;

    /**
     * 按库位分页扫描库存，填充 {@link InitializeInventoryCommand#getPairs()} 并返回分页元数据。
     */
    public InitializeInventoryFillMeta fillPairs(InitializeInventoryCommand request) {
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "locationId 不能为空");
        }
        int maxLines = ChsInventoryConstants.INITIALIZE_INVENTORY_DEFAULT_MAX_LINES;
        if (request.getMaxLines() != null && request.getMaxLines() > 0) {
            maxLines = request.getMaxLines();
        }
        int startPage = request.getPageNo() != null && request.getPageNo() >= 0 ? request.getPageNo() : 0;
        boolean skipInitialized = request.getSkipInitialized() == null || request.getSkipInitialized();

        String operatorName = resolveCurrentOperatorName();
        String invDate = DATE_FMT.format(java.time.LocalDate.now());
        String invChgTime = DATE_TIME_FMT.format(LocalDateTime.now());
        Long tenantId = TenantUtils.getCurrentTenantId();

        InventoryItemPageQuery query = new InventoryItemPageQuery();
        query.setLocationId(request.getLocationId());
        query.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        query.setInventoryStatusEnum(PublicationStatus.ACTIVE.getCode());

        List<InitializeInventoryPairCommand> pairs = new ArrayList<>();
        int skippedAlready = 0;
        int currentPage = startPage;
        long totalElements = 0;
        int totalPages = 0;
        boolean hasMore = false;

        while (pairs.size() < maxLines) {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(
                query, PageRequest.of(currentPage, maxLines, Sort.by(Sort.Direction.ASC, "id")));
            if (currentPage == startPage) {
                totalElements = page.getTotalElements();
                totalPages = page.getTotalPages();
            }
            if (page.isEmpty()) {
                break;
            }
            for (InventoryItemModel item : page.getContent()) {
                if (pairs.size() >= maxLines) {
                    break;
                }
                if (item == null || item.getId() == null) {
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (skipInitialized && invUploadQueryService.isInitializePairComplete(tenantId, item.getId())) {
                    skippedAlready++;
                    continue;
                }
                MedicationDefinition medication = medicationDefinitionQueryService
                    .findOptionalMedicationDefinitionEntityById(item.getItemId())
                    .orElse(null);
                if (medication == null) {
                    continue;
                }
                InitializeInventoryPairCommand pair = buildPair(
                    item, medication, invDate, invChgTime, operatorName, request.getRemark());
                if (pair != null) {
                    pairs.add(pair);
                }
            }
            if (currentPage + 1 < page.getTotalPages()) {
                hasMore = true;
                currentPage++;
            } else {
                hasMore = false;
                break;
            }
        }

        request.setPairs(pairs);
        return new InitializeInventoryFillMeta()
            .setPageNo(startPage)
            .setNextPageNo(hasMore ? currentPage + 1 : null)
            .setHasMore(hasMore)
            .setTotalElements(totalElements)
            .setTotalPages(totalPages)
            .setSkippedAlreadyInitialized(skippedAlready);
    }

    private InitializeInventoryPairCommand buildPair(
        InventoryItemModel item,
        MedicationDefinition medication,
        String invDate,
        String invChgTime,
        String operatorName,
        String remark
    ) {
        String fixmedinsBchno = isNotEmpty(item.getLotNumber())
            ? item.getLotNumber().trim() : trimToNull(medication.getBusNo());
        if (isEmpty(fixmedinsBchno)) {
            return null;
        }
        InvUploadLineCommand line3501 = new InvUploadLineCommand();
        line3501.setSourceLineId(item.getId());
        line3501.setMedListCodg(trimToNull(medication.getYbNo()));
        line3501.setFixmedinsHilistId(medication.getBusNo());
        line3501.setFixmedinsHilistName(medication.getName());
        line3501.setFixmedinsBchno(fixmedinsBchno);
        line3501.setManuLotnum(item.getLotNumber());
        line3501.setInvCnt(item.getQuantity());
        line3501.setInvdate(invDate);
        line3501.setRxFlag(Boolean.TRUE.equals(medication.getRxFlag()) ? "1" : "0");
        line3501.setMemo(remark);
        if (item.getProductionDate() != null) {
            line3501.setManuDate(item.getProductionDate().toLocalDate().format(DATE_FMT));
        }
        if (item.getExpirationDate() != null) {
            line3501.setExpyEnd(item.getExpirationDate().toLocalDate().format(DATE_FMT));
        }
        if (isEmpty(line3501.getMedListCodg())) {
            line3501.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }

        InvUploadLineCommand line3502 = new InvUploadLineCommand();
        line3502.setSourceLineId(item.getId());
        line3502.setMedListCodg(line3501.getMedListCodg());
        line3502.setFixmedinsHilistId(line3501.getFixmedinsHilistId());
        line3502.setFixmedinsHilistName(line3501.getFixmedinsHilistName());
        line3502.setFixmedinsBchno(fixmedinsBchno);
        line3502.setManuLotnum(item.getLotNumber());
        line3502.setCnt(item.getQuantity());
        line3502.setInvChgType(InvChgType.OTHER_IN.getCode());
        line3502.setInvChgTime(invChgTime);
        line3502.setInvChgOpterName(operatorName);
        line3502.setRxFlag(line3501.getRxFlag());
        line3502.setMemo(remark);
        if (isEmpty(line3502.getMedListCodg())) {
            line3502.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }
        if (item.getProductionDate() != null) {
            line3502.setManuDate(line3501.getManuDate());
        }
        if (item.getExpirationDate() != null) {
            line3502.setExpyEnd(line3501.getExpyEnd());
        }

        return new InitializeInventoryPairCommand()
            .setStocktake3501(line3501)
            .setInvChange3502(line3502);
    }

    private String resolveCurrentOperatorName() {
        try {
            Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
            Practitioner practitioner = practitionerQueryService.findById(operatorId);
            if (practitioner != null) {
                return practitioner.getName();
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }
}
