/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.service;

import com.openhis.health.domain.custom.tracenumber.entity.TraceNumber;
import com.openhis.health.domain.custom.tracenumber.entity.TraceNumberRecord;
import com.openhis.health.domain.custom.tracenumber.queries.InHospitalTraceNumberPageQuery;
import com.openhis.health.domain.custom.tracenumber.queries.TraceNumberReportPageQuery;
import com.openhis.health.domain.custom.tracenumber.queries.TraceRecordPageQuery;
import com.openhis.health.domain.custom.tracenumber.queries.model.InHospitalTraceNumberPageQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.InventoryItemTraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.LocationTraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberIdentityQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberReportRowQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceRecordPageQueryModel;
import com.openhis.health.domain.custom.tracenumber.repository.TraceNumberRecordRepository;
import com.openhis.health.domain.custom.tracenumber.repository.TraceNumberRepository;
import com.openhis.health.domain.custom.tracenumber.support.TraceNumberLocationNetSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TraceNumberConstants;
import com.openhis.health.domain.share.enums.InHospitalStatus;
import com.openhis.health.domain.share.enums.LocationStockStatus;
import com.openhis.health.domain.share.enums.TraceIdentityResolveStatus;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 追溯码读侧查询服务（DM-CUS-010 §6.2）。
 *
 * <p>包路径 {@code com.openhis.health.domain.custom.tracenumber}；仅本聚合仓储，禁止跨聚合读（B.6.4）。
 * 库位轧差直读子表 {@code split_quantity}。本期仅药品（{@link TableNameConstants#MED_MEDICATION_DEFINITION}）。</p>
 */
@Service
@RequiredArgsConstructor
public class TraceNumberQueryService {

    private final TraceNumberRepository traceNumberRepository;
    private final TraceNumberRecordRepository traceNumberRecordRepository;

    /**
     * 按追溯码查主档（DM §6.2.1）。
     *
     * @param traceNo 追溯码
     * @return 主档读模型
     * @throws BusinessException 追溯码为空或主档不存在时
     */
    public TraceNumberQueryModel getTraceNumberByTraceNo(String traceNo) {
        String normalized = trimToNull(traceNo);
        if (normalized == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码不能为空");
        }
        TraceNumber master = traceNumberRepository.findByTraceNoAndIsDeletedFalse(normalized)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.TRACE_NUMBER_NOT_FOUND, "追溯码主档不存在"));
        return this.toTraceNumberQueryModel(master);
    }

    /**
     * 按追溯码查记录时间序（DM §6.2.2）。
     *
     * @param traceNo 追溯码
     * @return 记录列表；追溯码为空时返回空列表
     */
    public List<TraceNumberRecordQueryModel> listRecordsByTraceNo(String traceNo) {
        String normalized = trimToNull(traceNo);
        if (normalized == null) {
            return new ArrayList<>();
        }
        List<TraceNumberRecord> records =
            traceNumberRecordRepository.findAllByTraceNoAndIsDeletedFalseOrderByIdAsc(normalized);
        List<TraceNumberRecordQueryModel> result = new ArrayList<>();
        for (TraceNumberRecord record : records) {
            result.add(this.toRecordQueryModel(record));
        }
        return result;
    }

    /**
     * 院内在院码分页（DM §6.2.3）；主表 {@code in_hospital_status_enum=IN_HOSPITAL}。
     *
     * @param query 分页筛选；可空时仅按在院态与药品表过滤
     * @return 分页结果
     */
    public Page<InHospitalTraceNumberPageQueryModel> pageInHospitalTraceNumbers(InHospitalTraceNumberPageQuery query) {
        InHospitalTraceNumberPageQuery effectiveQuery = query;
        if (effectiveQuery == null) {
            effectiveQuery = new InHospitalTraceNumberPageQuery();
        }
        Pageable pageable = PageRequest.of(
            effectiveQuery.springPageIndex(),
            effectiveQuery.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT)
        );
        Specification<TraceNumber> specification = this.buildInHospitalSpecification(effectiveQuery);
        Page<TraceNumber> page = traceNumberRepository.findAll(specification, pageable);
        List<InHospitalTraceNumberPageQueryModel> rows = new ArrayList<>();
        for (TraceNumber master : page.getContent()) {
            InHospitalTraceNumberPageQueryModel row = new InHospitalTraceNumberPageQueryModel();
            this.copyTraceNumberQueryModel(this.toTraceNumberQueryModel(master), row);
            rows.add(row);
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * 按库位查子表聚合净量大于 0 的追溯码列表（DM §6.2.4 · BR-TNR-013）。
     *
     * @param locationId 库位标识
     * @return 库位关联码列表；库位为空时返回空列表
     */
    public List<LocationTraceNumberQueryModel> listTraceNumbersByLocationId(Long locationId) {
        if (locationId == null) {
            return new ArrayList<>();
        }
        List<TraceNumberRecord> records = traceNumberRecordRepository.findAllByLocationIdAndIsDeletedFalse(locationId);
        Map<String, BigDecimal> netByTraceNo = TraceNumberLocationNetSupport.aggregateNetSplitByTraceNo(records);
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(netByTraceNo.keySet());
        List<LocationTraceNumberQueryModel> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : netByTraceNo.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            TraceNumber master = masterByTraceNo.get(entry.getKey());
            if (master == null) {
                continue;
            }
            LocationTraceNumberQueryModel row = new LocationTraceNumberQueryModel();
            row.setTraceNo(entry.getKey());
            row.setItemId(master.getItemId());
            row.setLotNumber(master.getLotNumber());
            row.setLocationNetSplitQuantity(entry.getValue());
            result.add(row);
        }
        return result;
    }

    /**
     * 追溯记录审计分页（DM §6.2.5）。
     *
     * @param query 筛选分页；可空时仅软删过滤
     * @return 分页结果
     */
    public Page<TraceRecordPageQueryModel> getTraceRecordPage(TraceRecordPageQuery query) {
        TraceRecordPageQuery effectiveQuery = query;
        if (effectiveQuery == null) {
            effectiveQuery = new TraceRecordPageQuery();
        }
        Pageable pageable = PageRequest.of(
            effectiveQuery.springPageIndex(),
            effectiveQuery.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT)
        );
        Specification<TraceNumberRecord> specification = this.buildRecordPageSpecification(effectiveQuery);
        Page<TraceNumberRecord> page = traceNumberRecordRepository.findAll(specification, pageable);
        List<TraceRecordPageQueryModel> rows = new ArrayList<>();
        for (TraceNumberRecord record : page.getContent()) {
            TraceRecordPageQueryModel row = new TraceRecordPageQueryModel();
            this.copyRecordQueryModel(this.toRecordQueryModel(record), row);
            rows.add(row);
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * 批量扫码身份反查（DM §6.2.6 · BR-TNR-015）；只读不写库。
     *
     * @param traceNos 追溯码列表；请求内重复码第二条起标记 {@code DUPLICATE_IN_REQUEST}
     * @return 与入参同序的解析结果，未命中亦占位返回状态
     */
    public List<TraceNumberIdentityQueryModel> batchResolveTraceIdentityByTraceNos(List<String> traceNos) {
        if (traceNos == null || traceNos.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> orderedNormalized = new ArrayList<>();
        Set<String> seenInRequest = new HashSet<>();
        Set<String> duplicateInRequest = new HashSet<>();
        for (String traceNo : traceNos) {
            String normalized = trimToNull(traceNo);
            if (normalized == null) {
                orderedNormalized.add("");
                continue;
            }
            if (seenInRequest.contains(normalized)) {
                duplicateInRequest.add(normalized);
            } else {
                seenInRequest.add(normalized);
            }
            orderedNormalized.add(normalized);
        }
        Set<String> uniqueForLoad = new HashSet<>(seenInRequest);
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(uniqueForLoad);
        List<TraceNumberIdentityQueryModel> result = new ArrayList<>();
        Set<String> firstOccurrenceEmitted = new HashSet<>();
        for (String normalized : orderedNormalized) {
            TraceNumberIdentityQueryModel row = new TraceNumberIdentityQueryModel();
            row.setTraceNo(normalized);
            if (normalized.isEmpty()) {
                row.setResolveStatusEnum(TraceIdentityResolveStatus.NOT_FOUND.getCode());
                result.add(row);
                continue;
            }
            if (duplicateInRequest.contains(normalized) && firstOccurrenceEmitted.contains(normalized)) {
                row.setResolveStatusEnum(TraceIdentityResolveStatus.DUPLICATE_IN_REQUEST.getCode());
                result.add(row);
                continue;
            }
            firstOccurrenceEmitted.add(normalized);
            TraceNumber master = masterByTraceNo.get(normalized);
            if (master == null) {
                row.setResolveStatusEnum(TraceIdentityResolveStatus.NOT_FOUND.getCode());
                result.add(row);
                continue;
            }
            row.setItemTable(master.getItemTable());
            row.setItemId(master.getItemId());
            row.setLotNumber(master.getLotNumber());
            row.setTraceUnitCode(master.getTraceUnitCode());
            row.setRemainingSplitQuantity(master.getRemainingSplitQuantity());
            row.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
            if (InHospitalStatus.NOT_IN_HOSPITAL.getCode().equals(master.getInHospitalStatusEnum())
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                row.setResolveStatusEnum(TraceIdentityResolveStatus.NOT_IN_HOSPITAL.getCode());
            } else {
                row.setResolveStatusEnum(TraceIdentityResolveStatus.OK.getCode());
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 按库存行查本库仍在库追溯码（DM §6.2.7 · IF-TRC-22）；子表轧差净拆零量 &gt; 0。
     *
     * @param locationId 库位
     * @param itemTable  品项表（本期仅 {@link TableNameConstants#MED_MEDICATION_DEFINITION}）
     * @param itemId     药品定义标识
     * @param lotNumber  批号
     * @return 本库仍在库码列表
     * @throws BusinessException 品项表非药品时
     */
    public List<InventoryItemTraceNumberQueryModel> listInStockTraceNumbersByInventoryItemKey(
        Long locationId,
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
        if (locationId == null || itemId == null) {
            return new ArrayList<>();
        }
        if (StringUtils.isNotEmpty(itemTable)
            && !TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable.trim())) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_MEDICATION_ONLY, "本期仅支持药品追溯码");
        }
        String normalizedLot;
        if (lotNumber == null) {
            normalizedLot = "";
        } else {
            normalizedLot = lotNumber.trim();
        }
        List<TraceNumberRecord> records = traceNumberRecordRepository.findAllByLocationIdAndIsDeletedFalse(locationId);
        Map<String, BigDecimal> netByTraceNo = TraceNumberLocationNetSupport.aggregateNetSplitByTraceNo(records);
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(netByTraceNo.keySet());
        List<InventoryItemTraceNumberQueryModel> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : netByTraceNo.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            TraceNumber master = masterByTraceNo.get(entry.getKey());
            if (master == null) {
                continue;
            }
            if (!itemId.equals(master.getItemId())) {
                continue;
            }
            if (!normalizedLot.equals(master.getLotNumber())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(master.getItemTable())) {
                continue;
            }
            InventoryItemTraceNumberQueryModel row = new InventoryItemTraceNumberQueryModel();
            row.setTraceNo(entry.getKey());
            row.setLocationNetSplitQuantity(entry.getValue());
            row.setTraceUnitCode(master.getTraceUnitCode());
            row.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
            result.add(row);
        }
        return result;
    }

    /**
     * 按库存四维查子表流水列表（CHS 3505/3506 读路径 · 非轧差）。
     *
     * <p>返回指定库位下、主档品项批号匹配的**全部**子表流水；调用方按 {@code recordDirectionEnum}/{@code busNo} 过滤。</p>
     *
     * @param locationId 库位
     * @param itemTable  品项表（可空，默认药品表）
     * @param itemId     药品定义标识
     * @param lotNumber  批号
     * @return 流水读模型列表；参数不全时返回空列表
     */
    public List<TraceNumberRecordQueryModel> listFlowRecordsByInventoryItemKey(
        Long locationId,
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
        if (locationId == null || itemId == null) {
            return new ArrayList<>();
        }
        String normalizedTable;
        if (StringUtils.isEmpty(itemTable)) {
            normalizedTable = TableNameConstants.MED_MEDICATION_DEFINITION;
        } else {
            normalizedTable = itemTable.trim();
        }
        String normalizedLot;
        if (lotNumber == null) {
            normalizedLot = "";
        } else {
            normalizedLot = lotNumber.trim();
        }
        List<TraceNumberRecord> records = traceNumberRecordRepository.findAllByLocationIdAndIsDeletedFalse(locationId);
        Set<String> traceNos = new HashSet<>();
        for (TraceNumberRecord record : records) {
            if (record == null || StringUtils.isEmpty(record.getTraceNo())) {
                continue;
            }
            if (!normalizedLot.equals(record.getLotNumber())) {
                continue;
            }
            traceNos.add(record.getTraceNo().trim());
        }
        if (traceNos.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(traceNos);
        List<TraceNumberRecordQueryModel> result = new ArrayList<>();
        for (TraceNumberRecord record : records) {
            if (record == null || StringUtils.isEmpty(record.getTraceNo())) {
                continue;
            }
            if (!normalizedLot.equals(record.getLotNumber())) {
                continue;
            }
            TraceNumber master = masterByTraceNo.get(record.getTraceNo().trim());
            if (master == null) {
                continue;
            }
            if (!itemId.equals(master.getItemId())) {
                continue;
            }
            if (!normalizedTable.equals(master.getItemTable())) {
                continue;
            }
            result.add(this.toRecordQueryModel(record));
        }
        return result;
    }

    /**
     * 追溯码报表分页（DM §6.2.8 · AS-CUS-TRC-002）；默认按 {@code lastRecordAt} 降序。
     *
     * <p>DOC-CONFLICT：{@code locationStockStatusEnum} 在当前页内存过滤，总条数仍为主表分页 total。</p>
     *
     * @param query 报表入参
     * @return 分页行
     * @throws BusinessException {@code queryScope=LOCATION} 且未选库位时
     */
    public Page<TraceNumberReportRowQueryModel> pageTraceNumberReport(TraceNumberReportPageQuery query) {
        TraceNumberReportPageQuery effectiveQuery = query;
        if (effectiveQuery == null) {
            effectiveQuery = new TraceNumberReportPageQuery();
        }
        if (TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(effectiveQuery.getQueryScope())
            && effectiveQuery.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "指定库房查询须选择库位");
        }
        Pageable pageable = PageRequest.of(
            effectiveQuery.springPageIndex(),
            effectiveQuery.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT)
        );
        Specification<TraceNumber> specification = this.buildReportSpecification(effectiveQuery);
        Page<TraceNumber> page = traceNumberRepository.findAll(specification, pageable);
        Set<Long> masterIds = new HashSet<>();
        for (TraceNumber master : page.getContent()) {
            masterIds.add(master.getId());
        }
        Map<Long, LocalDateTime> lastRecordAtByMasterId = this.loadLastRecordAtByMasterIds(masterIds);
        Long locationId = effectiveQuery.getLocationId();
        Map<String, BigDecimal> locationNetByTraceNo = new HashMap<>();
        if (TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(effectiveQuery.getQueryScope()) && locationId != null) {
            List<TraceNumberRecord> locationRecords =
                traceNumberRecordRepository.findAllByLocationIdAndIsDeletedFalse(locationId);
            locationNetByTraceNo = TraceNumberLocationNetSupport.aggregateNetSplitByTraceNo(locationRecords);
        }
        List<TraceNumberReportRowQueryModel> rows = new ArrayList<>();
        for (TraceNumber master : page.getContent()) {
            TraceNumberReportRowQueryModel row = new TraceNumberReportRowQueryModel();
            row.setTraceNo(master.getTraceNo());
            row.setItemId(master.getItemId());
            row.setLotNumber(master.getLotNumber());
            row.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
            row.setRemainingSplitQuantity(master.getRemainingSplitQuantity());
            row.setTraceUnitCode(master.getTraceUnitCode());
            row.setLastFlowAt(lastRecordAtByMasterId.get(master.getId()));
            if (TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(effectiveQuery.getQueryScope())) {
                BigDecimal locationNet = locationNetByTraceNo.get(master.getTraceNo());
                if (locationNet == null) {
                    locationNet = BigDecimal.ZERO;
                }
                row.setLocationNetSplitQuantity(locationNet);
                if (locationNet.compareTo(BigDecimal.ZERO) > 0) {
                    row.setLocationStockStatusEnum(LocationStockStatus.IN_LOCATION.getCode());
                } else {
                    row.setLocationStockStatusEnum(LocationStockStatus.OUT_OF_LOCATION.getCode());
                }
                String locationStockFilter = effectiveQuery.getLocationStockStatusEnum();
                if (StringUtils.isNotEmpty(locationStockFilter)) {
                    if (!locationStockFilter.equals(row.getLocationStockStatusEnum())) {
                        continue;
                    }
                }
            }
            rows.add(row);
        }
        rows.sort((left, right) -> {
            LocalDateTime leftAt = left.getLastFlowAt();
            LocalDateTime rightAt = right.getLastFlowAt();
            if (leftAt == null && rightAt == null) {
                return 0;
            }
            if (leftAt == null) {
                return 1;
            }
            if (rightAt == null) {
                return -1;
            }
            return rightAt.compareTo(leftAt);
        });
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * 断言同一请求内追溯码不重复（AS-CUS-TRC-001 · BR-TRC-015）。
     *
     * @param traceCodes 追溯码集合
     * @throws BusinessException 请求内重复时
     */
    public void assertNoDuplicateTraceCodes(Collection<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            if (normalized == null) {
                continue;
            }
            // 同一请求重复提交相同码须拒绝，避免双 OUT
            if (seen.contains(normalized)) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_DUPLICATE_IN_REQUEST,
                    "同一请求内追溯码重复"
                );
            }
            seen.add(normalized);
        }
    }

    /**
     * 断言追溯码主档在院可用（全院语义 · BR-TRC-006），替代单表时代「已出库」校验。
     *
     * @param traceCodes 追溯码集合
     * @throws BusinessException 任一码不在院或剩余耗尽时
     */
    public void assertCodesInHospitalForDispense(Collection<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        Set<String> normalizedCodes = new HashSet<>();
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            if (normalized != null) {
                normalizedCodes.add(normalized);
            }
        }
        if (normalizedCodes.isEmpty()) {
            return;
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(normalizedCodes);
        for (String normalized : normalizedCodes) {
            TraceNumber master = masterByTraceNo.get(normalized);
            // 主档不存在视为不可用
            if (master == null) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或已耗尽"
                );
            }
            if (InHospitalStatus.NOT_IN_HOSPITAL.getCode().equals(master.getInHospitalStatusEnum())
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或已耗尽"
                );
            }
        }
    }

    /**
     * 按发放行四维校验追溯码在本库净量大于 0（子表聚合 · BR-TRC-007 可选 Gate）。
     *
     * @param itemTable  品项表
     * @param itemId     药品标识
     * @param lotNumber  批号
     * @param locationId 库位
     * @param traceCodes 追溯码集合
     * @throws BusinessException 码不在本库或与品项批号不匹配时
     */
    public void assertCodesInNetStockForDispenseLine(
        String itemTable,
        Long itemId,
        String lotNumber,
        Long locationId,
        Collection<String> traceCodes
    ) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        if (itemId == null || locationId == null || StringUtils.isEmpty(itemTable)) {
            throw new BusinessException(
                ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                "追溯码校验缺少物品或库房维度"
            );
        }
        List<InventoryItemTraceNumberQueryModel> inStockRows =
            this.listInStockTraceNumbersByInventoryItemKey(locationId, itemTable, itemId, lotNumber);
        Set<String> inStockCodes = new HashSet<>();
        for (InventoryItemTraceNumberQueryModel row : inStockRows) {
            if (row != null && StringUtils.isNotEmpty(row.getTraceNo())) {
                inStockCodes.add(row.getTraceNo().trim());
            }
        }
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            if (normalized == null) {
                continue;
            }
            // 本库子表轧差净量须大于 0
            if (!inStockCodes.contains(normalized)) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在库或与药品、批号、库房不匹配"
                );
            }
        }
    }

    /**
     * 发药 OUT 前断言：每码须能覆盖 1 个销售包装（业务单位为销售包装时），与 {@code buildPerCodeLineItems} 一致。
     *
     * @param traceCodes             追溯码集合
     * @param lineQuantity           发药行业务数量
     * @param unitCode               发药业务单位
     * @param unitConversionSource   药品单位换算源
     * @throws BusinessException 码数与发药数量不一致或剩余不足时
     */
    public void assertRemainingSplitSufficientForDispenseOut(
        Collection<String> traceCodes,
        BigDecimal lineQuantity,
        String unitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        if (lineQuantity == null || lineQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                "发药数量须大于 0"
            );
        }
        List<String> codes = new ArrayList<>();
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            if (normalized != null) {
                codes.add(normalized);
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        String packageUnit = null;
        if (unitConversionSource != null && StringUtils.isNotEmpty(unitConversionSource.getPackageUnitCode())) {
            packageUnit = unitConversionSource.getPackageUnitCode().trim();
        }
        boolean onePackagePerCode = packageUnit != null
            && StringUtils.isNotEmpty(unitCode)
            && unitCode.trim().equals(packageUnit);
        BigDecimal totalRequiredSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            unitConversionSource,
            unitCode,
            lineQuantity
        );
        BigDecimal perCodeRequiredSplit;
        if (onePackagePerCode) {
            perCodeRequiredSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                unitConversionSource,
                packageUnit,
                BigDecimal.ONE
            );
            if (lineQuantity.stripTrailingZeros().scale() <= 0
                && BigDecimal.valueOf(codes.size()).compareTo(lineQuantity) != 0) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                    "追溯码个数须与发药数量一致（发"
                        + lineQuantity.stripTrailingZeros().toPlainString()
                        + packageUnit
                        + "应绑"
                        + lineQuantity.stripTrailingZeros().toPlainString()
                        + "个码，当前"
                        + codes.size()
                        + "个）"
                );
            }
        } else {
            perCodeRequiredSplit = totalRequiredSplit;
            if (codes.size() > 1) {
                perCodeRequiredSplit = totalRequiredSplit.divide(
                    BigDecimal.valueOf(codes.size()), 6, RoundingMode.HALF_UP);
            }
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(new HashSet<>(codes));
        for (String normalized : codes) {
            TraceNumber master = masterByTraceNo.get(normalized);
            if (master == null) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或已耗尽"
                );
            }
            if (master.getRemainingSplitQuantity().compareTo(perCodeRequiredSplit) < 0) {
                String remainingText = master.getRemainingSplitQuantity().stripTrailingZeros().toPlainString();
                String perCodeText = perCodeRequiredSplit.stripTrailingZeros().toPlainString();
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码" + normalized + "剩余拆零量" + remainingText
                        + "不足1盒（需" + perCodeText + "拆零量）；"
                        + "该码入库时可能被多码分摊导致不足整盒，请换扫其他在库码"
                );
            }
        }
    }

    /**
     * 院内在院码分页 JPA 谓词：软删、药品表、在院态及可选品项/码/批号筛选。
     *
     * @param query 分页筛选入参
     * @return 主档查询规约
     */
    private Specification<TraceNumber> buildInHospitalSpecification(InHospitalTraceNumberPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.IN_HOSPITAL_STATUS_ENUM),
                InHospitalStatus.IN_HOSPITAL.getCode()
            ));
            if (query.getItemId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
            }
            if (StringUtils.isNotEmpty(query.getTraceNoKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getTraceNoKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
            }
            if (StringUtils.isNotEmpty(query.getLotNumberKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getLotNumberKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), like));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 追溯记录审计分页 JPA 谓词：软删及码/库位/操作类型/单号/时间窗筛选。
     *
     * @param query 记录分页入参
     * @return 子表查询规约
     */
    private Specification<TraceNumberRecord> buildRecordPageSpecification(TraceRecordPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (StringUtils.isNotEmpty(query.getTraceNoKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getTraceNoKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
            }
            if (query.getLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
            }
            if (StringUtils.isNotEmpty(query.getOperationType())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.OPERATION_TYPE), query.getOperationType().trim()));
            }
            if (StringUtils.isNotEmpty(query.getBusNo())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.BUS_NO), query.getBusNo().trim()));
            }
            if (query.getRecordTimeStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.CREATED_AT),
                    query.getRecordTimeStart()
                ));
            }
            if (query.getRecordTimeEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.CREATED_AT),
                    query.getRecordTimeEnd()
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 追溯码报表分页 JPA 谓词：药品主档及在院态/品项/码/批号/流水时间 EXISTS 子查询。
     *
     * @param query 报表入参
     * @return 主档查询规约
     */
    private Specification<TraceNumber> buildReportSpecification(TraceNumberReportPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION));
            if (StringUtils.isNotEmpty(query.getInHospitalStatusEnum())) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.IN_HOSPITAL_STATUS_ENUM),
                    query.getInHospitalStatusEnum().trim()
                ));
            }
            if (query.getMedicationItemIds() != null && !query.getMedicationItemIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.ITEM_ID).in(query.getMedicationItemIds()));
            }
            if (StringUtils.isNotEmpty(query.getTraceNoKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getTraceNoKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
            }
            if (StringUtils.isNotEmpty(query.getLotNumberKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getLotNumberKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), like));
            }
            if (query.getFlowTimeStart() != null || query.getFlowTimeEnd() != null) {
                Subquery<Long> subquery = cq.subquery(Long.class);
                Root<TraceNumberRecord> recordRoot = subquery.from(TraceNumberRecord.class);
                subquery.select(recordRoot.get(FieldNameConstants.ID));
                List<Predicate> recordPredicates = new ArrayList<>();
                recordPredicates.add(cb.equal(
                    recordRoot.get(FieldNameConstants.TRACE_NUMBER_ID),
                    root.get(FieldNameConstants.ID)
                ));
                recordPredicates.add(cb.isFalse(recordRoot.get(FieldNameConstants.IS_DELETED)));
                if (query.getFlowTimeStart() != null) {
                    recordPredicates.add(cb.greaterThanOrEqualTo(
                        recordRoot.get(FieldNameConstants.CREATED_AT),
                        query.getFlowTimeStart()
                    ));
                }
                if (query.getFlowTimeEnd() != null) {
                    recordPredicates.add(cb.lessThanOrEqualTo(
                        recordRoot.get(FieldNameConstants.CREATED_AT),
                        query.getFlowTimeEnd()
                    ));
                }
                subquery.where(recordPredicates.toArray(Predicate[]::new));
                predicates.add(cb.exists(subquery));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 按追溯码批量加载主档并建 traceNo → 实体映射。
     *
     * @param traceNos 追溯码集合；空则返回空 Map
     * @return 主档映射
     */
    private Map<String, TraceNumber> loadMastersByTraceNos(Set<String> traceNos) {
        if (traceNos == null || traceNos.isEmpty()) {
            return new HashMap<>();
        }
        List<TraceNumber> masters = traceNumberRepository.findAllByTraceNoInAndIsDeletedFalse(traceNos);
        Map<String, TraceNumber> map = new HashMap<>();
        for (TraceNumber master : masters) {
            map.put(master.getTraceNo(), master);
        }
        return map;
    }

    /**
     * 按主档 id 聚合各码最近一条子表流水的创建时刻。
     *
     * @param masterIds 主档主键集合
     * @return 主档 id → 最近流水时间
     */
    private Map<Long, LocalDateTime> loadLastRecordAtByMasterIds(Set<Long> masterIds) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (masterIds == null || masterIds.isEmpty()) {
            return result;
        }
        List<TraceNumberRecord> records = traceNumberRecordRepository.findAllByTraceNumberIdInAndIsDeletedFalse(masterIds);
        for (TraceNumberRecord record : records) {
            LocalDateTime createdAt = record.getCreatedAt();
            if (createdAt == null) {
                continue;
            }
            LocalDateTime existing = result.get(record.getTraceNumberId());
            if (existing == null || createdAt.isAfter(existing)) {
                result.put(record.getTraceNumberId(), createdAt);
            }
        }
        return result;
    }

    /**
     * 追溯码主档实体 → 主档读模型。
     *
     * @param master 主档实体
     * @return 读模型
     */
    private TraceNumberQueryModel toTraceNumberQueryModel(TraceNumber master) {
        TraceNumberQueryModel model = new TraceNumberQueryModel();
        model.setId(master.getId());
        model.setTraceNo(master.getTraceNo());
        model.setItemTable(master.getItemTable());
        model.setItemId(master.getItemId());
        model.setLotNumber(master.getLotNumber());
        model.setTraceUnitCode(master.getTraceUnitCode());
        model.setRemainingSplitQuantity(master.getRemainingSplitQuantity());
        model.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
        return model;
    }

    /**
     * 主档读模型字段拷贝到目标实例（分页行复用）。
     *
     * @param source 源读模型
     * @param target 目标读模型
     */
    private void copyTraceNumberQueryModel(TraceNumberQueryModel source, TraceNumberQueryModel target) {
        target.setId(source.getId());
        target.setTraceNo(source.getTraceNo());
        target.setItemTable(source.getItemTable());
        target.setItemId(source.getItemId());
        target.setLotNumber(source.getLotNumber());
        target.setTraceUnitCode(source.getTraceUnitCode());
        target.setRemainingSplitQuantity(source.getRemainingSplitQuantity());
        target.setInHospitalStatusEnum(source.getInHospitalStatusEnum());
    }

    /**
     * 追溯子表流水实体 → 记录读模型。
     *
     * @param record 子表实体
     * @return 记录读模型
     */
    private TraceNumberRecordQueryModel toRecordQueryModel(TraceNumberRecord record) {
        TraceNumberRecordQueryModel model = new TraceNumberRecordQueryModel();
        model.setId(record.getId());
        model.setTraceNo(record.getTraceNo());
        model.setRecordCategoryEnum(record.getRecordCategoryEnum());
        model.setRecordDirectionEnum(record.getRecordDirectionEnum());
        model.setLocationId(record.getLocationId());
        model.setQuantity(record.getQuantity());
        model.setUnitCode(record.getUnitCode());
        model.setSplitQuantity(record.getSplitQuantity());
        model.setOperationType(record.getOperationType());
        model.setBusNo(record.getBusNo());
        model.setCreatedAt(record.getCreatedAt());
        return model;
    }

    /**
     * 记录读模型字段拷贝到审计分页行 DTO。
     *
     * @param source 源记录读模型
     * @param target 审计分页行
     */
    private void copyRecordQueryModel(TraceNumberRecordQueryModel source, TraceRecordPageQueryModel target) {
        target.setId(source.getId());
        target.setTraceNo(source.getTraceNo());
        target.setRecordCategoryEnum(source.getRecordCategoryEnum());
        target.setRecordDirectionEnum(source.getRecordDirectionEnum());
        target.setLocationId(source.getLocationId());
        target.setQuantity(source.getQuantity());
        target.setUnitCode(source.getUnitCode());
        target.setSplitQuantity(source.getSplitQuantity());
        target.setOperationType(source.getOperationType());
        target.setBusNo(source.getBusNo());
        target.setCreatedAt(source.getCreatedAt());
    }
}
