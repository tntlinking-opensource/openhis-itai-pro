/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InitializeInventoryPairCommand;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.InitializeInventoryRequest;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryFillMeta;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 从库房/药房现存药品库存组装首次初始化 3501+3502 配对请求（分页 + 续跑跳过）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryInitializeUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDefinitionRepository medicationDefinitionRepository;
    private final PractitionerQueryService practitionerQueryService;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadRepository uploadRepository;

    public InitializeInventoryFillMeta fillPairs(InitializeInventoryRequest request) {
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "locationId 不能为空");
        }
        int maxLines = ChsInventoryConstants.INITIALIZE_INVENTORY_DEFAULT_MAX_LINES;
        if (request.getMaxLines() != null && request.getMaxLines() > 0) {
            maxLines = request.getMaxLines();
        }
        int startPage;
        if (request.getPageNo() != null && request.getPageNo() >= 0) {
            startPage = request.getPageNo();
        } else {
            startPage = 0;
        }
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
                query,
                PageRequest.of(currentPage, maxLines, Sort.by(Sort.Direction.ASC, "id")));
            if (currentPage == startPage) {
                totalElements = page.getTotalElements();
                totalPages = page.getTotalPages();
            }
            if (page.isEmpty()) {
                hasMore = false;
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
                if (skipInitialized && isAlreadyInitialized(tenantId, item.getId())) {
                    skippedAlready++;
                    continue;
                }
                MedicationDefinition medication = medicationDefinitionRepository.findById(item.getItemId()).orElse(null);
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
        Integer nextPageNo;
        if (hasMore) {
            nextPageNo = currentPage + 1;
        } else {
            nextPageNo = null;
        }
        return new InitializeInventoryFillMeta()
            .setPageNo(startPage)
            .setNextPageNo(nextPageNo)
            .setHasMore(hasMore)
            .setTotalElements(totalElements)
            .setTotalPages(totalPages)
            .setSkippedAlreadyInitialized(skippedAlready);
    }

    private boolean isAlreadyInitialized(Long tenantId, Long inventoryItemId) {
        Optional<YbInvUpload> row3501 = uploadRepository.findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
            tenantId,
            ChsInfNo.INVENTORY_STOCKTAKE,
            inventoryItemId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (row3501.isEmpty()
            || !ChsInventoryConstants.SOURCE_TYPE_INIT.equals(row3501.get().getSourceType())) {
            return false;
        }
        Optional<YbInvUpload> row3502 = uploadRepository.findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
            tenantId,
            ChsInfNo.INVENTORY_CHANGE,
            inventoryItemId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        return row3502.isPresent()
            && ChsInventoryConstants.SOURCE_TYPE_INIT.equals(row3502.get().getSourceType());
    }

    private InitializeInventoryPairCommand buildPair(
        InventoryItemModel item,
        MedicationDefinition medication,
        String invDate,
        String invChgTime,
        String operatorName,
        String remark
    ) {
        String fixmedinsBchno;
        if (StringUtils.isNotBlank(item.getLotNumber())) {
            fixmedinsBchno = item.getLotNumber().trim();
        } else {
            fixmedinsBchno = trimToNull(medication.getBusNo());
        }
        if (StringUtils.isBlank(fixmedinsBchno)) {
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
        if (Boolean.TRUE.equals(medication.getRxFlag())) {
            line3501.setRxFlag("1");
        } else {
            line3501.setRxFlag("0");
        }
        line3501.setMemo(remark);
        if (item.getProductionDate() != null) {
            line3501.setManuDate(item.getProductionDate().toLocalDate().format(DATE_FMT));
        }
        if (item.getExpirationDate() != null) {
            line3501.setExpyEnd(item.getExpirationDate().toLocalDate().format(DATE_FMT));
        }
        if (StringUtils.isBlank(line3501.getMedListCodg())) {
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
        line3502.setInvChgType(ChsInventoryConstants.INV_CHG_TYPE_OTHER_IN);
        line3502.setInvChgTime(invChgTime);
        line3502.setInvChgOpterName(operatorName);
        line3502.setRxFlag(line3501.getRxFlag());
        line3502.setMemo(remark);
        if (StringUtils.isBlank(line3502.getMedListCodg())) {
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
            Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
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