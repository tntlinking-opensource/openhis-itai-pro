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
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceUnitSnapshotQueryModel;
import com.openhis.health.domain.custom.tracenumber.repository.TraceNumberRecordRepository;
import com.openhis.health.domain.custom.tracenumber.repository.TraceNumberRepository;
import com.openhis.health.domain.custom.tracenumber.support.TraceNumberLocationNetSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TraceNumberConstants;
import com.openhis.health.domain.share.enums.InHospitalStatus;
import com.openhis.health.domain.share.enums.LocationStockStatus;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceIdentityResolveStatus;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * 按追溯码查主档单位（进销存拆零判定用）；无主档时 empty。
     *
     * @param traceNo 追溯码
     * @return 主档 {@code traceUnitCode}；码空或不存在时 empty
     */
    public Optional<String> findTraceUnitCodeByTraceNo(String traceNo) {
        String normalized = trimToNull(traceNo);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<TraceNumber> master = traceNumberRepository.findByTraceNoAndIsDeletedFalse(normalized);
        if (master.isEmpty()) {
            return Optional.empty();
        }
        String unit = trimToNull(master.get().getTraceUnitCode());
        if (unit == null) {
            return Optional.empty();
        }
        return Optional.of(unit);
    }

    /**
     * 按追溯码查主档贴码比例快照（发退换算用）；无主档或快照非法时 empty。
     *
     * @param traceNo 追溯码
     * @return 主档 {@code traceUnitRatio}；码空、不存在或非正时 empty
     */
    public Optional<BigDecimal> findTraceUnitRatioByTraceNo(String traceNo) {
        String normalized = trimToNull(traceNo);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<TraceNumber> master = traceNumberRepository.findByTraceNoAndIsDeletedFalse(normalized);
        if (master.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal ratio = master.get().getTraceUnitRatio();
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(ratio);
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
        // 有码/批号关键字时无 Sort，保留 Spec 精确优先 ORDER BY（B.2.1）
        Pageable pageable;
        boolean hasKeyword = StringUtils.isNotEmpty(effectiveQuery.getTraceNoKeyword())
            || StringUtils.isNotEmpty(effectiveQuery.getLotNumberKeyword());
        if (hasKeyword) {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize());
        } else {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize(),
                Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        }
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
        Pageable pageable;
        if (StringUtils.isNotEmpty(effectiveQuery.getTraceNoKeyword())) {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize());
        } else {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize(),
                Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        }
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
            // 主档贴码比：发药应扫换算 / 扫码后校正待扫
            row.setTraceUnitRatio(master.getTraceUnitRatio());
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
     * 按库存行查本库仍在库追溯码（DM §6.2.7 · IF-TRC-22）。
     *
     * <p>条件：本库子表轧差净拆零 &gt; 0，且主档 {@code IN_HOSPITAL}、剩余拆零 &gt; 0。
     * 流转未扫码时子表净量可能滞后，须以主档消耗态为准，避免把已发药 OUT 的码仍当本库在库。</p>
     *
     * @param locationId 库位
     * @param itemTable  品项表（本期仅 {@link TableNameConstants#MED_MEDICATION_DEFINITION}）
     * @param itemId     药品定义标识
     * @param lotNumber  批号
     * @return 本库仍在库且主档未耗尽的码列表
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
            // 本库净量已耗尽：不算在库
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
            // 主档已出院或剩余耗尽：发药等 HOSPITAL_IO OUT 已消耗，禁止因流转未扫导致子表净量滞后而仍当本库在库
            if (!InHospitalStatus.IN_HOSPITAL.getCode().equals(master.getInHospitalStatusEnum())) {
                continue;
            }
            if (master.getRemainingSplitQuantity() == null
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InventoryItemTraceNumberQueryModel row = new InventoryItemTraceNumberQueryModel();
            row.setTraceNo(entry.getKey());
            row.setLotNumber(master.getLotNumber());
            row.setLocationNetSplitQuantity(entry.getValue());
            // 主档拆零剩余：与本库净量并存，供全院浏览/盖住判定读侧
            row.setRemainingSplitQuantity(master.getRemainingSplitQuantity());
            row.setTraceUnitCode(master.getTraceUnitCode());
            // 主档贴码比快照：发药待扫换算真源（非药典目录）
            row.setTraceUnitRatio(master.getTraceUnitRatio());
            row.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
            result.add(row);
        }
        return result;
    }

    /**
     * 按药品查全院仍在院追溯码（主表 {@code IN_HOSPITAL} 且剩余拆零 &gt; 0；含全部批号）。
     *
     * <p>扫码弹窗「药品 → 批号列表 → 码」浏览用；仅本聚合主档仓储，不读库位子表。</p>
     *
     * @param itemTable 品项表；空则默认药品表
     * @param itemId    药品定义标识
     * @return 全院在院码列表（含 {@code lotNumber}）；参数不全时返回空列表
     * @throws BusinessException 品项表非药品时
     */
    public List<InventoryItemTraceNumberQueryModel> listInHospitalTraceNumbersByItem(
        String itemTable,
        Long itemId
    ) {
        if (itemId == null) {
            return new ArrayList<>();
        }
        String effectiveItemTable = itemTable;
        if (StringUtils.isEmpty(effectiveItemTable)) {
            effectiveItemTable = TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(effectiveItemTable.trim())) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_MEDICATION_ONLY, "本期仅支持药品追溯码");
        }
        String itemTableFinal = effectiveItemTable.trim();
        Long itemIdFinal = itemId;
        Specification<TraceNumber> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTableFinal));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemIdFinal));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.IN_HOSPITAL_STATUS_ENUM),
                InHospitalStatus.IN_HOSPITAL.getCode()
            ));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<TraceNumber> masters = traceNumberRepository.findAll(spec);
        List<InventoryItemTraceNumberQueryModel> result = new ArrayList<>();
        for (TraceNumber master : masters) {
            if (master == null) {
                continue;
            }
            // 剩余耗尽的主档不可再发
            if (master.getRemainingSplitQuantity() == null
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InventoryItemTraceNumberQueryModel row = new InventoryItemTraceNumberQueryModel();
            row.setTraceNo(master.getTraceNo());
            row.setLotNumber(master.getLotNumber());
            // 兼容旧调用方仍读 locationNetSplitQuantity；真源字段为 remainingSplitQuantity
            row.setLocationNetSplitQuantity(master.getRemainingSplitQuantity());
            row.setRemainingSplitQuantity(master.getRemainingSplitQuantity());
            row.setTraceUnitCode(master.getTraceUnitCode());
            row.setTraceUnitRatio(master.getTraceUnitRatio());
            row.setInHospitalStatusEnum(master.getInHospitalStatusEnum());
            result.add(row);
        }
        return result;
    }

    /**
     * 按药品+批号查全院仍在院追溯码（主表 {@code IN_HOSPITAL} 且剩余拆零 &gt; 0）。
     *
     * <p>流转未扫码时本库子表可能无净量；发药扫码允许用同品同批全院在院码。仅本聚合主档仓储。</p>
     *
     * @param itemTable 品项表；空则默认药品表
     * @param itemId    药品定义标识
     * @param lotNumber 批号
     * @return 全院在院码列表；参数不全时返回空列表
     * @throws BusinessException 品项表非药品时
     */
    public List<InventoryItemTraceNumberQueryModel> listInHospitalTraceNumbersByItemLot(
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
        String normalizedLot;
        if (lotNumber == null) {
            normalizedLot = "";
        } else {
            normalizedLot = lotNumber.trim();
        }
        if (normalizedLot.isEmpty()) {
            return new ArrayList<>();
        }
        List<InventoryItemTraceNumberQueryModel> all =
            this.listInHospitalTraceNumbersByItem(itemTable, itemId);
        List<InventoryItemTraceNumberQueryModel> result = new ArrayList<>();
        for (InventoryItemTraceNumberQueryModel row : all) {
            if (row == null) {
                continue;
            }
            String rowLot = row.getLotNumber();
            if (rowLot == null) {
                continue;
            }
            // 仅保留入参批号
            if (!normalizedLot.equals(rowLot.trim())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 主表是否已有该品批追溯码（与在院态、剩余量无关）。
     *
     * <p>有码批：发药须硬校验在库/在院并写主表流水；无码批（期初未扫）：仅业务行 {@code trace_no}，
     * 数量提示软校验、不写主表流水（AS-CUS-TRC-001 · BR-TRC-LOT-SOFT）。</p>
     *
     * @param itemTable 品项表；空则默认药品表
     * @param itemId    品项主键
     * @param lotNumber 批号
     * @return 主表存在未删除该品批码则为 true；参数不全为 false
     */
    public boolean existsMasterByItemLot(String itemTable, Long itemId, String lotNumber) {
        if (itemId == null) {
            return false;
        }
        String effectiveItemTable = itemTable;
        if (StringUtils.isEmpty(effectiveItemTable)) {
            effectiveItemTable = TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        String normalizedLot = trimToNull(lotNumber);
        if (normalizedLot == null) {
            return false;
        }
        return this.traceNumberRepository.existsByItemTableAndItemIdAndLotNumberAndIsDeletedFalse(
            effectiveItemTable.trim(),
            itemId,
            normalizedLot
        );
    }

    /**
     * 按品+批取主档贴码单位/比例快照（DD §4.3 结算；不限在院态）。
     *
     * <p>同品批多码时取首条 {@code trace_unit_ratio &gt; 0} 的快照；无主档或快照非法时 empty，
     * 由调用方回落药典。</p>
     *
     * @param itemTable 品项表；可空默认药品定义表
     * @param itemId    药品定义主键
     * @param lotNumber 批号
     * @return 主档贴码单位+比例；无可用快照时 empty
     */
    public Optional<TraceUnitSnapshotQueryModel> findTraceUnitSnapshotByItemLot(
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
        // 无品项主键：无法查主表
        if (itemId == null) {
            return Optional.empty();
        }
        String effectiveItemTable = itemTable;
        // 品项表未传：默认药品定义表
        if (StringUtils.isEmpty(effectiveItemTable)) {
            effectiveItemTable = TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        String normalizedLot = trimToNull(lotNumber);
        // 批号空：无法定位品批快照
        if (normalizedLot == null) {
            return Optional.empty();
        }
        String itemTableFinal = effectiveItemTable.trim();
        String lotFinal = normalizedLot;
        Long itemIdFinal = itemId;
        Specification<TraceNumber> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTableFinal));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemIdFinal));
            predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), lotFinal));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<TraceNumber> masters = this.traceNumberRepository.findAll(spec);
        for (TraceNumber master : masters) {
            // 仓储偶发空元素：跳过
            if (master == null) {
                continue;
            }
            String unit = trimToNull(master.getTraceUnitCode());
            BigDecimal ratio = master.getTraceUnitRatio();
            // 缺单位：跳过该码，继续找同品批其它主档
            if (unit == null) {
                continue;
            }
            // 比例非正：跳过该码
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            TraceUnitSnapshotQueryModel snapshot = new TraceUnitSnapshotQueryModel();
            snapshot.setTraceUnitCode(unit);
            snapshot.setTraceUnitRatio(ratio);
            return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    /**
     * 批量按品+批取主档贴码快照（结算 ContextLoader 防 N+1；键同 {@link #buildItemLotMasterExistsMapKey}）。
     *
     * @param keys 品批键
     * @return mapKey → 快照；无合法快照的键不入 map
     */
    public Map<String, TraceUnitSnapshotQueryModel> batchFindTraceUnitSnapshotByItemLot(
        List<ItemLotMasterExistsLookupKey> keys
    ) {
        Map<String, TraceUnitSnapshotQueryModel> result = new HashMap<>();
        // 无品批键：直接空 map
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        Set<Long> itemIds = new HashSet<>();
        Set<String> wantedKeys = new HashSet<>();
        for (ItemLotMasterExistsLookupKey key : keys) {
            // 键或缺品项主键：无法参与批量查
            if (key == null || key.itemId() == null) {
                continue;
            }
            String mapKey = this.buildItemLotMasterExistsMapKey(key.itemTable(), key.itemId(), key.lotNumber());
            wantedKeys.add(mapKey);
            itemIds.add(key.itemId());
        }
        // 本批无有效品项：无需查库
        if (itemIds.isEmpty()) {
            return result;
        }
        Specification<TraceNumber> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<TraceNumber> masters = this.traceNumberRepository.findAll(spec);
        for (TraceNumber master : masters) {
            // 空主档或无品项：跳过
            if (master == null || master.getItemId() == null) {
                continue;
            }
            String mapKey = this.buildItemLotMasterExistsMapKey(
                master.getItemTable(),
                master.getItemId(),
                master.getLotNumber()
            );
            // 非本批请求的品批：跳过
            if (!wantedKeys.contains(mapKey)) {
                continue;
            }
            // 已取到合法快照：同品批只留首条
            if (result.containsKey(mapKey)) {
                continue;
            }
            String unit = trimToNull(master.getTraceUnitCode());
            BigDecimal ratio = master.getTraceUnitRatio();
            // 缺单位：跳过该码
            if (unit == null) {
                continue;
            }
            // 比例非正：跳过该码
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            TraceUnitSnapshotQueryModel snapshot = new TraceUnitSnapshotQueryModel();
            snapshot.setTraceUnitCode(unit);
            snapshot.setTraceUnitRatio(ratio);
            result.put(mapKey, snapshot);
        }
        return result;
    }

    /**
     * 批量判断主表是否已有品批码（发药 slip 回填 {@code lotHasTraceMaster}）。
     *
     * @param keys 品批键；元素含 itemTable/itemId/lotNumber
     * @return mapKey({@link #buildItemLotMasterExistsMapKey}) → 是否有主档
     */
    public Map<String, Boolean> batchExistsMasterByItemLot(List<ItemLotMasterExistsLookupKey> keys) {
        Map<String, Boolean> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        Set<Long> itemIds = new HashSet<>();
        for (ItemLotMasterExistsLookupKey key : keys) {
            if (key == null || key.itemId() == null) {
                continue;
            }
            itemIds.add(key.itemId());
            result.put(this.buildItemLotMasterExistsMapKey(key.itemTable(), key.itemId(), key.lotNumber()), false);
        }
        if (itemIds.isEmpty()) {
            return result;
        }
        Specification<TraceNumber> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<TraceNumber> masters = this.traceNumberRepository.findAll(spec);
        for (TraceNumber master : masters) {
            if (master == null || master.getItemId() == null) {
                continue;
            }
            String mapKey = this.buildItemLotMasterExistsMapKey(
                master.getItemTable(),
                master.getItemId(),
                master.getLotNumber()
            );
            // 命中任一未删除主档即标有码批
            if (result.containsKey(mapKey)) {
                result.put(mapKey, true);
            }
        }
        return result;
    }

    /**
     * 组装品批「是否有主档」map 键。
     *
     * @param itemTable 品项表
     * @param itemId    品项
     * @param lotNumber 批号
     * @return 稳定键
     */
    public String buildItemLotMasterExistsMapKey(String itemTable, Long itemId, String lotNumber) {
        String table;
        if (StringUtils.isEmpty(itemTable)) {
            table = TableNameConstants.MED_MEDICATION_DEFINITION;
        } else {
            table = itemTable.trim();
        }
        String lot;
        if (lotNumber == null) {
            lot = "";
        } else {
            lot = lotNumber.trim();
        }
        return table + "|" + itemId + "|" + lot;
    }

    /**
     * 品批主档存在性批量查询键。
     *
     * @param itemTable 品项表；可空默认药品
     * @param itemId    品项主键
     * @param lotNumber 批号
     */
    public record ItemLotMasterExistsLookupKey(String itemTable, Long itemId, String lotNumber) {
    }

    /**
     * 解析发药应扫换算用的主档贴码快照：本库在库码优先，否则全院同品同批在院码。
     *
     * <p>比值取 {@code cus_trace_number.trace_unit_ratio}，禁止用目录比顶替。</p>
     *
     * @param locationId 发放库房；可空（空则直接走全院）
     * @param itemTable  品项表；可空默认药品
     * @param itemId     药品定义标识
     * @param lotNumber  批号
     * @return 主档贴码单位+比例；无可用码时 empty
     */
    public Optional<TraceUnitSnapshotQueryModel> resolvePreferredTraceUnitSnapshot(
        Long locationId,
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
        if (itemId == null) {
            return Optional.empty();
        }
        // Pre：本库有在库码时优先取其主档快照（流转已扫入本库）
        if (locationId != null) {
            List<InventoryItemTraceNumberQueryModel> locationRows =
                this.listInStockTraceNumbersByInventoryItemKey(locationId, itemTable, itemId, lotNumber);
            Optional<TraceUnitSnapshotQueryModel> fromLocation = this.firstValidTraceUnitSnapshot(locationRows);
            if (fromLocation.isPresent()) {
                return fromLocation;
            }
        }
        // 本库无码：全院同品同批在院主档（医院流转常不扫码）
        List<InventoryItemTraceNumberQueryModel> hospitalRows =
            this.listInHospitalTraceNumbersByItemLot(itemTable, itemId, lotNumber);
        return this.firstValidTraceUnitSnapshot(hospitalRows);
    }

    /**
     * 批量解析主档贴码快照（按 locationId|itemId|lotNumber 键；同键复用一次查询结果）。
     *
     * @param keys 键列表；元素为 {@code locationId}（可空）、{@code itemTable}、{@code itemId}、{@code lotNumber}
     * @return 键 → 快照；无码的键不入 map
     */
    public Map<String, TraceUnitSnapshotQueryModel> batchResolvePreferredTraceUnitSnapshots(
        List<TraceUnitSnapshotLookupKey> keys
    ) {
        Map<String, TraceUnitSnapshotQueryModel> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        for (TraceUnitSnapshotLookupKey key : keys) {
            if (key == null || key.itemId() == null) {
                continue;
            }
            String mapKey = this.buildTraceUnitSnapshotMapKey(
                key.locationId(), key.itemId(), key.lotNumber());
            if (result.containsKey(mapKey)) {
                continue;
            }
            Optional<TraceUnitSnapshotQueryModel> snapshot = this.resolvePreferredTraceUnitSnapshot(
                key.locationId(),
                key.itemTable(),
                key.itemId(),
                key.lotNumber()
            );
            if (snapshot.isPresent()) {
                result.put(mapKey, snapshot.get());
            }
        }
        return result;
    }

    /**
     * 发药主档贴码快照批量查找键。
     *
     * @param locationId 库房（可空）
     * @param itemTable  品项表（可空）
     * @param itemId     药品 id
     * @param lotNumber  批号
     */
    public record TraceUnitSnapshotLookupKey(
        Long locationId,
        String itemTable,
        Long itemId,
        String lotNumber
    ) {
    }

    /**
     * 组装主档快照 map 键（与 {@link #batchResolvePreferredTraceUnitSnapshots} 一致）。
     *
     * @param locationId 库房
     * @param itemId     药品
     * @param lotNumber  批号
     * @return 稳定键
     */
    public String buildTraceUnitSnapshotMapKey(Long locationId, Long itemId, String lotNumber) {
        String loc;
        if (locationId == null) {
            loc = "";
        } else {
            loc = String.valueOf(locationId);
        }
        String lot;
        if (lotNumber == null) {
            lot = "";
        } else {
            lot = lotNumber.trim();
        }
        return loc + "|" + itemId + "|" + lot;
    }

    /**
     * 从在库/在院码列表取首个合法主档贴码快照。
     *
     * @param rows 码行
     * @return 快照；无合法比例时 empty
     */
    private Optional<TraceUnitSnapshotQueryModel> firstValidTraceUnitSnapshot(
        List<InventoryItemTraceNumberQueryModel> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        for (InventoryItemTraceNumberQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            String unit = trimToNull(row.getTraceUnitCode());
            BigDecimal ratio = row.getTraceUnitRatio();
            if (unit == null) {
                continue;
            }
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            TraceUnitSnapshotQueryModel snapshot = new TraceUnitSnapshotQueryModel();
            snapshot.setTraceUnitCode(unit);
            snapshot.setTraceUnitRatio(ratio);
            return Optional.of(snapshot);
        }
        return Optional.empty();
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
     * <p>指定库房范围：仅返回该库房轧差净量 &gt; 0 的码（能查出来即在本库）；
     * 列表行带 {@code currentLocationIds} 供应用层展示「当前所在库房」。</p>
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
        boolean locationScope =
            TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(effectiveQuery.getQueryScope());
        if (locationScope && effectiveQuery.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "指定库房查询须选择库位");
        }
        Pageable pageable;
        boolean hasKeyword = StringUtils.isNotEmpty(effectiveQuery.getTraceNoKeyword())
            || StringUtils.isNotEmpty(effectiveQuery.getLotNumberKeyword());
        if (hasKeyword) {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize());
        } else {
            pageable = PageRequest.of(
                effectiveQuery.springPageIndex(),
                effectiveQuery.getBoundedPageSize(),
                Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        }
        // Pre：指定库房 → 先轧差本库在库码集合，空则直接空页
        Set<String> locationInStockTraceNos = null;
        Map<String, BigDecimal> locationNetByTraceNo = new HashMap<>();
        if (locationScope) {
            List<TraceNumberRecord> locationRecords =
                traceNumberRecordRepository.findAllByLocationIdAndIsDeletedFalse(effectiveQuery.getLocationId());
            locationNetByTraceNo = TraceNumberLocationNetSupport.aggregateNetSplitByTraceNo(locationRecords);
            locationInStockTraceNos = new HashSet<>();
            for (Map.Entry<String, BigDecimal> entry : locationNetByTraceNo.entrySet()) {
                BigDecimal net = entry.getValue();
                if (net != null && net.compareTo(BigDecimal.ZERO) > 0) {
                    locationInStockTraceNos.add(entry.getKey());
                }
            }
            if (locationInStockTraceNos.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0L);
            }
        }
        Specification<TraceNumber> specification =
            this.buildReportSpecification(effectiveQuery, locationInStockTraceNos);
        Page<TraceNumber> page = traceNumberRepository.findAll(specification, pageable);
        Set<Long> masterIds = new HashSet<>();
        for (TraceNumber master : page.getContent()) {
            masterIds.add(master.getId());
        }
        Map<Long, LocalDateTime> lastRecordAtByMasterId = this.loadLastRecordAtByMasterIds(masterIds);
        // Pre：当前页码 → 各库位净量，解析当前所在库房
        Map<String, List<Long>> currentLocationIdsByTraceNo = Map.of();
        if (!masterIds.isEmpty()) {
            List<TraceNumberRecord> pageRecords =
                traceNumberRecordRepository.findAllByTraceNumberIdInAndIsDeletedFalse(masterIds);
            currentLocationIdsByTraceNo =
                TraceNumberLocationNetSupport.resolveCurrentLocationIdsByTraceNo(pageRecords);
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
            if (locationScope) {
                BigDecimal locationNet = locationNetByTraceNo.get(master.getTraceNo());
                if (locationNet == null) {
                    locationNet = BigDecimal.ZERO;
                }
                row.setLocationNetSplitQuantity(locationNet);
                // Asm：指定库房命中即在本库；当前所在库房 = 所选库房
                row.setCurrentLocationIds(List.of(effectiveQuery.getLocationId()));
                row.setLocationStockStatusEnum(LocationStockStatus.IN_LOCATION.getCode());
            } else {
                row.setCurrentLocationIds(currentLocationIdsByTraceNo.get(master.getTraceNo()));
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
     * 断言给定码集合内无重复（发药口径：仅校验<strong>单行</strong>逗号串内不重复）。
     *
     * <p>消耗/发药侧<strong>允许</strong>同一追溯码出现在多行（箱码/同品多明细共扫一码，见 DD §3.4 /
     * AS-CUS-TRC-001 · BR-TRC-023）。跨行同码不得调用本方法对「整单拼表」做硬拒。
     * IF-TRC-21 单次反查请求内重复仍由 {@link #batchResolveTraceIdentityByTraceNos} 标记
     * {@code DUPLICATE_IN_REQUEST}（与业务行绑定去重无关）。</p>
     *
     * @param traceCodes 通常为一行展开后的码列表
     * @throws BusinessException 集合内出现重复码时
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
            // 单行内同码重复：硬拒（禁止一行绑两次相同码）
            if (seen.contains(normalized)) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_DUPLICATE_IN_REQUEST,
                    "同一明细行内追溯码重复"
                );
            }
            seen.add(normalized);
        }
    }

    /**
     * 发药硬 Gate：主档已存在且拆零剩余 ≤ 0 时拒绝再用该码（BR-TRC-006 消耗侧例外）。
     *
     * <p>主档不存在（未建档）不抛——消耗侧仍软放行仅记业务行。仅「找得到主档但剩余已耗尽」硬拦。</p>
     *
     * @param traceCodes 提交/扫入的追溯码集合
     * @throws BusinessException 任一码主档存在且剩余 ≤ 0
     */
    public void assertMasterCodesNotExhaustedForDispense(Collection<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        Set<String> normalizedCodes = new HashSet<>();
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            // 空白片段忽略
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
            // 主档 trace_no 未 trim 时兜底按去空白匹配
            if (master == null) {
                for (TraceNumber candidate : masterByTraceNo.values()) {
                    if (candidate == null) {
                        continue;
                    }
                    if (normalized.equals(trimToNull(candidate.getTraceNo()))) {
                        master = candidate;
                        break;
                    }
                }
            }
            // 未建档：消耗侧软放行，不在此硬拦
            if (master == null) {
                continue;
            }
            BigDecimal remaining = master.getRemainingSplitQuantity();
            // 主档剩余空或 ≤0：禁止再扫/再提交该码发药
            if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码剩余已耗尽，不可再使用"
                );
            }
        }
    }

    /**
     * 断言追溯码主档在院可用（全院语义 · BR-TRC-006 读侧能力）。
     *
     * <p><b>消耗/发药保存配药不得整段调用</b>（未建档/品批不匹配仍软）；耗尽硬拦请用
     * {@link #assertMasterCodesNotExhaustedForDispense}。</p>
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
     * 发药行追溯码可用性校验：本库净量优先；本库无该码时允许全院同品同批在院码。
     *
     * <p><b>消耗/发药保存配药不得调用</b>（BR-TRC-006/020：码身份硬 Gate 仅进货；未建档/品批不匹配仅前端软提示，
     * 业务行可存码，核发写流水软跳过）。本方法保留读侧/诊断复用。</p>
     *
     * <p>医院场景库房间流转常不扫码，子表本库净量可能为 0，但主档仍 {@code IN_HOSPITAL} 且剩余 &gt; 0；
     * 此时只要扫码主档的 {@code itemId}+{@code lotNumber} 与发药行一致即可通过。</p>
     *
     * @param itemTable  品项表
     * @param itemId     药品标识
     * @param lotNumber  批号
     * @param locationId 库位（本库优先匹配用）
     * @param traceCodes 追溯码集合
     * @throws BusinessException 码不在院、已耗尽或与品项批号不匹配时
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
        String normalizedLot;
        if (lotNumber == null) {
            normalizedLot = "";
        } else {
            normalizedLot = lotNumber.trim();
        }
        // Pre：本库在库码集合（有则直接过）
        List<InventoryItemTraceNumberQueryModel> inStockRows =
            this.listInStockTraceNumbersByInventoryItemKey(locationId, itemTable, itemId, lotNumber);
        Set<String> inStockCodes = new HashSet<>();
        for (InventoryItemTraceNumberQueryModel row : inStockRows) {
            if (row != null && StringUtils.isNotEmpty(row.getTraceNo())) {
                inStockCodes.add(row.getTraceNo().trim());
            }
        }
        Set<String> needHospitalCheck = new HashSet<>();
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            if (normalized == null) {
                continue;
            }
            // 本库子表轧差净量 > 0：优先通过
            if (inStockCodes.contains(normalized)) {
                continue;
            }
            needHospitalCheck.add(normalized);
        }
        // 本库未命中：回落主档全院在院 + 同品同批
        if (needHospitalCheck.isEmpty()) {
            return;
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(needHospitalCheck);
        for (String normalized : needHospitalCheck) {
            TraceNumber master = masterByTraceNo.get(normalized);
            if (master == null) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或与药品、批号不匹配"
                );
            }
            // 品项表须为本期药品
            if (!itemTable.trim().equals(master.getItemTable())) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或与药品、批号不匹配"
                );
            }
            if (!itemId.equals(master.getItemId())) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或与药品、批号不匹配"
                );
            }
            if (!normalizedLot.equals(master.getLotNumber())) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或与药品、批号不匹配"
                );
            }
            if (InHospitalStatus.NOT_IN_HOSPITAL.getCode().equals(master.getInHospitalStatusEnum())
                || master.getRemainingSplitQuantity() == null
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码不在院内或已耗尽"
                );
            }
        }
    }

    /**
     * 消耗侧发药 OUT 覆盖判定（DD §3.1）：已扫码主档剩余合计 ≥ 本次操作拆零量即合法。
     *
     * <p>不再要求扫码个数等于 {@code ceil(量÷比)}；不足时返回 {@code false}，<b>不抛</b>，
     * 由调用方跳过写追溯（等同未扫），避免扫码卡住发药主流程。空码集合视为未扫，返回 {@code false}。</p>
     *
     * @param traceCodes           追溯码集合
     * @param lineQuantity         发药行业务数量
     * @param unitCode             发药业务单位
     * @param unitConversionSource 药品单位换算源（可空；缺主档快照时回落目录）
     * @return 已扫且剩余合计盖住本次拆零量为 true；否则 false
     */
    public boolean isRemainingSplitSufficientForDispenseOut(
        Collection<String> traceCodes,
        BigDecimal lineQuantity,
        String unitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        Optional<BigDecimal> totalRequired = this.resolveDispenseOutRequiredSplit(
            traceCodes, lineQuantity, unitCode, unitConversionSource);
        // 无法换算或无有效码：软判定不足，不写 OUT（不挡发药主流程）
        if (totalRequired.isEmpty()) {
            return false;
        }
        List<String> codes = this.normalizeTraceCodeList(traceCodes);
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(new HashSet<>(codes));
        BigDecimal remainingSum = BigDecimal.ZERO;
        for (String normalized : codes) {
            TraceNumber master = masterByTraceNo.get(normalized);
            // 缺主档或剩余非正：该码不计入覆盖
            if (master == null
                || master.getRemainingSplitQuantity() == null
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            remainingSum = remainingSum.add(master.getRemainingSplitQuantity());
        }
        return remainingSum.compareTo(totalRequired.get()) >= 0;
    }

    /**
     * 消耗侧剩余先出分摊（DD §3.2）：在已扫集合内按剩余升序扣减，同分比建档时间、再比码串。
     *
     * <p>仅返回实扣 &gt; 0 的码；覆盖不足时返回 empty，禁止带着盖不住的码串写 OUT。</p>
     *
     * @param traceCodes           已扫追溯码（顺序仅交互用，分摊不依赖扫序）
     * @param lineQuantity         行业务数量
     * @param unitCode             行业务单位
     * @param unitConversionSource 换算源
     * @return 每码实扣拆零（有序）；不足或无法换算时 empty
     */
    public Optional<List<DispenseOutPerCodeSplit>> allocateRemainingFirstSplitForDispenseOut(
        Collection<String> traceCodes,
        BigDecimal lineQuantity,
        String unitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        Optional<BigDecimal> totalRequired = this.resolveDispenseOutRequiredSplit(
            traceCodes, lineQuantity, unitCode, unitConversionSource);
        // 无法换算或无有效码：软判定不足，不分摊
        if (totalRequired.isEmpty()) {
            return Optional.empty();
        }
        List<String> codes = this.normalizeTraceCodeList(traceCodes);
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(new HashSet<>(codes));
        List<TraceNumber> sortable = new ArrayList<>();
        for (String normalized : codes) {
            TraceNumber master = masterByTraceNo.get(normalized);
            // 无主档或剩余耗尽：不参与分摊
            if (master == null
                || master.getRemainingSplitQuantity() == null
                || master.getRemainingSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            sortable.add(master);
        }
        // 剩余少的先扣；同分建档时间升序；再比码串保证可复现
        sortable.sort((left, right) -> {
            int byRemaining = left.getRemainingSplitQuantity().compareTo(right.getRemainingSplitQuantity());
            // 剩余量不同：少者优先
            if (byRemaining != 0) {
                return byRemaining;
            }
            LocalDateTime leftCreated = left.getCreatedAt();
            LocalDateTime rightCreated = right.getCreatedAt();
            // 双方有建档时间：早建档先扣
            if (leftCreated != null && rightCreated != null) {
                int byCreated = leftCreated.compareTo(rightCreated);
                // 建档时刻不同：按时序
                if (byCreated != 0) {
                    return byCreated;
                }
            } else if (leftCreated == null && rightCreated != null) {
                // 左无建档时间：排前（稳定兜底）
                return -1;
            } else if (leftCreated != null) {
                // 右无建档时间：右排前
                return 1;
            }
            return left.getTraceNo().compareTo(right.getTraceNo());
        });
        BigDecimal remainToAllocate = totalRequired.get();
        List<DispenseOutPerCodeSplit> allocated = new ArrayList<>();
        for (TraceNumber master : sortable) {
            // 已凑满则停止
            if (remainToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = master.getRemainingSplitQuantity();
            BigDecimal take = available;
            // 本码剩余大于待摊：只扣待摊量
            if (take.compareTo(remainToAllocate) > 0) {
                take = remainToAllocate;
            }
            // 本码无实扣则跳过
            if (take.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            DispenseOutPerCodeSplit row = new DispenseOutPerCodeSplit();
            row.setTraceNo(master.getTraceNo());
            row.setSplitQuantity(take);
            allocated.add(row);
            remainToAllocate = remainToAllocate.subtract(take);
        }
        // 盖不住：禁止部分写 OUT
        if (remainToAllocate.compareTo(BigDecimal.ZERO) > 0) {
            return Optional.empty();
        }
        // 无一码实扣：视为未扫，软判定不足
        if (allocated.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(allocated);
    }

    /**
     * 按发放单号查询该次发药 HOSPITAL_IO OUT 子表流水（id 升序 = 写侧落库序）。
     *
     * <p>供退药 IN 镜像（DD §5）：整单退全额加回；部分退按本序逆序耗尽。</p>
     *
     * @param busNo 发放单 {@code bus_no}
     * @return OUT 流水读模型；无单号或无流水时为空列表
     */
    public List<TraceNumberRecordQueryModel> listDispenseHospitalIoOutRecordsByBusNo(String busNo) {
        return this.listHospitalIoOutRecordsByBusNoAndOperationTypes(
            busNo,
            List.of(
                TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode(),
                TraceNumberRecordOperationType.MEDICATION_EXTRA_DISPENSE_OUT.getCode()
            )
        );
    }

    /**
     * 按业务单号 + 操作类型查询 HOSPITAL_IO OUT 流水（id 升序）。
     *
     * <p>领用退领镜像：operationType 传 {@code DEPT_ISSUE}（与 SupplyType code 同字面）。</p>
     *
     * @param busNo           业务单号
     * @param operationTypes  操作类型 code 集合；空则返回空列表
     * @return OUT 流水读模型
     */
    public List<TraceNumberRecordQueryModel> listHospitalIoOutRecordsByBusNoAndOperationTypes(
        String busNo,
        Collection<String> operationTypes
    ) {
        return this.listHospitalIoRecordsByBusNoDirectionAndOperationTypes(
            busNo, TraceDirection.OUT.getCode(), operationTypes);
    }

    /**
     * 按业务单号 + 操作类型查询 HOSPITAL_IO IN 流水（id 升序）。
     *
     * <p>供住院清床合并 3505：按码汇总发药 OUT 后减去退药 IN 实扣，判定 {@code trdn_flag}。</p>
     *
     * @param busNo           业务单号（退药发放行 bus_no）
     * @param operationTypes  操作类型 code 集合；空则返回空列表
     * @return IN 流水读模型
     */
    public List<TraceNumberRecordQueryModel> listHospitalIoInRecordsByBusNoAndOperationTypes(
        String busNo,
        Collection<String> operationTypes
    ) {
        return this.listHospitalIoRecordsByBusNoDirectionAndOperationTypes(
            busNo, TraceDirection.IN.getCode(), operationTypes);
    }

    /**
     * 按业务单号 + 方向 + 操作类型查询 HOSPITAL_IO 流水（id 升序）。
     *
     * @param busNo              业务单号
     * @param recordDirectionEnum 方向 code（OUT/IN）
     * @param operationTypes     操作类型 code 集合；空则返回空列表
     * @return 流水读模型
     */
    private List<TraceNumberRecordQueryModel> listHospitalIoRecordsByBusNoDirectionAndOperationTypes(
        String busNo,
        String recordDirectionEnum,
        Collection<String> operationTypes
    ) {
        String trimmedBusNo = trimToNull(busNo);
        // 无单号或未指定操作类型：无流水可查
        if (trimmedBusNo == null || operationTypes == null || operationTypes.isEmpty()) {
            return new ArrayList<>();
        }
        String direction = trimToNull(recordDirectionEnum);
        // 方向空：无法过滤 OUT/IN
        if (direction == null) {
            return new ArrayList<>();
        }
        Set<String> opSet = new HashSet<>();
        for (String op : operationTypes) {
            String normalized = trimToNull(op);
            // 有效操作类型入过滤集
            if (normalized != null) {
                opSet.add(normalized);
            }
        }
        // 清洗后无操作类型：直接空列表
        if (opSet.isEmpty()) {
            return new ArrayList<>();
        }
        List<TraceNumberRecord> flows =
            traceNumberRecordRepository.findAllByBusNoAndIsDeletedFalse(trimmedBusNo);
        List<TraceNumberRecordQueryModel> result = new ArrayList<>();
        for (TraceNumberRecord flow : flows) {
            // 空流水：跳过
            if (flow == null) {
                continue;
            }
            // 非院内出入类别：非发药/退药镜像源
            if (!TraceRecordCategory.HOSPITAL_IO.getCode().equals(flow.getRecordCategoryEnum())) {
                continue;
            }
            // 方向不匹配：跳过
            if (!direction.equals(flow.getRecordDirectionEnum())) {
                continue;
            }
            // 操作类型不在本批：跳过
            if (!opSet.contains(flow.getOperationType())) {
                continue;
            }
            result.add(this.toRecordQueryModel(flow));
        }
        result.sort((left, right) -> {
            Long leftId = left.getId();
            Long rightId = right.getId();
            // 双方无主键：视为相等
            if (leftId == null && rightId == null) {
                return 0;
            }
            // 左无主键：排前
            if (leftId == null) {
                return -1;
            }
            // 右无主键：右排前
            if (rightId == null) {
                return 1;
            }
            return leftId.compareTo(rightId);
        });
        return result;
    }

    /**
     * 解析发药 OUT 所需总拆零量；无有效码或无法换算时 empty。
     *
     * @param traceCodes           追溯码集合
     * @param lineQuantity         行业务数量
     * @param unitCode             行业务单位
     * @param unitConversionSource 换算源
     * @return 总拆零量
     */
    private Optional<BigDecimal> resolveDispenseOutRequiredSplit(
        Collection<String> traceCodes,
        BigDecimal lineQuantity,
        String unitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        List<String> codes = this.normalizeTraceCodeList(traceCodes);
        // 空码：未扫，不写 OUT
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        // 行数量非法：无法盖住判定，按不足处理（不挡主流程）
        if (lineQuantity == null || lineQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNos(new HashSet<>(codes));
        String traceUnitFromMaster = null;
        BigDecimal traceRatioFromMaster = null;
        for (String normalized : codes) {
            TraceNumber probe = masterByTraceNo.get(normalized);
            // 命中带贴码单位的主档：取作换算锚点
            if (probe != null && StringUtils.isNotEmpty(probe.getTraceUnitCode())) {
                traceUnitFromMaster = probe.getTraceUnitCode().trim();
                // 发退换算优先主档快照比，药典变更不漂移
                if (probe.getTraceUnitRatio() != null
                    && probe.getTraceUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                    traceRatioFromMaster = probe.getTraceUnitRatio();
                }
                break;
            }
        }
        String packageUnit = null;
        // 有换算源且含包装单位：备作主档单位缺失时的回落
        if (unitConversionSource != null && StringUtils.isNotEmpty(unitConversionSource.getPackageUnitCode())) {
            packageUnit = unitConversionSource.getPackageUnitCode().trim();
        }
        String compareUnit = traceUnitFromMaster;
        // 主档无贴码单位：回落包装单位
        if (StringUtils.isEmpty(compareUnit)) {
            compareUnit = packageUnit;
        }
        // 无贴码单位无法换算：视为盖不住，跳过写追溯
        if (StringUtils.isEmpty(compareUnit)) {
            return Optional.empty();
        }
        MedicationProductUnitConversionSource effectiveSource = unitConversionSource;
        // 有主档比例快照：覆盖换算源贴码比，避免药典漂移
        if (traceRatioFromMaster != null && unitConversionSource != null) {
            effectiveSource = this.copyConversionSourceWithTraceSnapshot(
                unitConversionSource,
                compareUnit,
                traceRatioFromMaster
            );
        }
        // 缺换算源时无法把业务量落到拆零
        if (effectiveSource == null) {
            return Optional.empty();
        }
        BigDecimal totalRequiredSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            effectiveSource,
            unitCode,
            lineQuantity
        );
        return Optional.of(totalRequiredSplit);
    }

    /**
     * 清洗追溯码集合：去空白、保序去重不在此做（调用方去重）。
     *
     * @param traceCodes 原始集合
     * @return 非空码列表
     */
    private List<String> normalizeTraceCodeList(Collection<String> traceCodes) {
        List<String> codes = new ArrayList<>();
        // 空集合：视为未扫
        if (traceCodes == null || traceCodes.isEmpty()) {
            return codes;
        }
        for (String code : traceCodes) {
            String normalized = trimToNull(code);
            // 空白码丢弃；有效码入列
            if (normalized != null) {
                codes.add(normalized);
            }
        }
        return codes;
    }

    /**
     * 发药 OUT 剩余先出：单码实扣拆零量。
     */
    public static final class DispenseOutPerCodeSplit {

        /** 追溯码。 */
        private String traceNo;

        /** 本码实扣拆零量。 */
        private BigDecimal splitQuantity;

        /**
         * @return 追溯码
         */
        public String getTraceNo() {
            return traceNo;
        }

        /**
         * @param traceNo 追溯码
         */
        public void setTraceNo(String traceNo) {
            this.traceNo = traceNo;
        }

        /**
         * @return 实扣拆零量
         */
        public BigDecimal getSplitQuantity() {
            return splitQuantity;
        }

        /**
         * @param splitQuantity 实扣拆零量
         */
        public void setSplitQuantity(BigDecimal splitQuantity) {
            this.splitQuantity = splitQuantity;
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
                String trimmed = query.getTraceNoKeyword().trim();
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
                // B.2.1：追溯码精确全等置顶
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                    root.get(FieldNameConstants.TRACE_NO));
            }
            if (StringUtils.isNotEmpty(query.getLotNumberKeyword())) {
                String trimmedLot = query.getLotNumberKeyword().trim();
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedLot);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), like));
                // 仅批号关键字时：批号精确优先；与码关键字并存时以后写 ORDER BY 为准（批号次级场景少）
                if (StringUtils.isEmpty(query.getTraceNoKeyword())) {
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmedLot,
                        List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                        root.get(FieldNameConstants.LOT_NUMBER));
                }
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
                String trimmed = query.getTraceNoKeyword().trim();
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
                // B.2.1：记录侧追溯码精确全等置顶
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                    root.get(FieldNameConstants.TRACE_NO));
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
    /**
     * 组装报表主表 Specification。
     *
     * @param query                   筛选入参
     * @param locationInStockTraceNos 指定库房本库在库码集合；null 表示不按库房码集合收窄
     * @return Specification
     */
    private Specification<TraceNumber> buildReportSpecification(
        TraceNumberReportPageQuery query,
        Set<String> locationInStockTraceNos) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION));
            // Gate：指定库房仅命中本库净量 > 0 的码
            if (locationInStockTraceNos != null) {
                predicates.add(root.get(FieldNameConstants.TRACE_NO).in(locationInStockTraceNos));
            }
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
                String trimmed = query.getTraceNoKeyword().trim();
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.TRACE_NO)), like));
                // B.2.1：报表追溯码精确全等置顶
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                    root.get(FieldNameConstants.TRACE_NO));
            }
            if (StringUtils.isNotEmpty(query.getLotNumberKeyword())) {
                String trimmedLot = query.getLotNumberKeyword().trim();
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedLot);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), like));
                if (StringUtils.isEmpty(query.getTraceNoKeyword())) {
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmedLot,
                        List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                        root.get(FieldNameConstants.LOT_NUMBER));
                }
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
     * 复制换算源并写入主档贴码单位与比例快照，供发退 Gate 与写侧一致。
     *
     * @param source     目录换算源
     * @param traceUnit  主档贴码单位
     * @param traceRatio 主档比例快照
     * @return 新换算源实例
     */
    private MedicationProductUnitConversionSource copyConversionSourceWithTraceSnapshot(
        MedicationProductUnitConversionSource source,
        String traceUnit,
        BigDecimal traceRatio
    ) {
        MedicationProductUnitConversionSource copy = new MedicationProductUnitConversionSource();
        copy.setSplitUnitCode(source.getSplitUnitCode());
        copy.setDoseUnitCode(source.getDoseUnitCode());
        copy.setDoseUnitRatio(source.getDoseUnitRatio());
        copy.setPurchaseUnitCode(source.getPurchaseUnitCode());
        copy.setPurchaseUnitRatio(source.getPurchaseUnitRatio());
        copy.setStockUnitCode(source.getStockUnitCode());
        copy.setStockUnitRatio(source.getStockUnitRatio());
        copy.setPackageUnitCode(source.getPackageUnitCode());
        copy.setPackageUnitRatio(source.getPackageUnitRatio());
        copy.setTraceUnitCode(traceUnit);
        copy.setTraceUnitRatio(traceRatio);
        return copy;
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
        model.setLotNumber(record.getLotNumber());
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
        target.setLotNumber(source.getLotNumber());
        target.setCreatedAt(source.getCreatedAt());
    }
}
