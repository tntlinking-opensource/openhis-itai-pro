/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDischargeSummarySalesCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 住院清床后汇总径净销售 3505 组装（发退相抵、按天+单价分桶合并）。
 *
 * <p>为何挂清床：结算完成后才有稳定结算上下文；汇总核发时追溯码未齐、且 3505 是患者销售接口。
 * 不同单价不合桶；不同药师可合（代表行取首条正向药师）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryDischargeSummaryUploadContextLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final EncounterQueryService encounterQueryService;
    private final InventoryUploadContextLoader dispenseUploadContextLoader;
    private final ChsInsuranceContractReaderImpl contractReader;

    /**
     * 组装清床汇总径净销售 3505 命令；无净量行时返回空 lines。
     *
     * @param command 就诊与契约
     * @return 3505 上报命令（{@code sourceType=CLEAR_BED_SUMMARY}）
     */
    public InvUpload3505Command build3505Request(UploadAfterDischargeSummarySalesCommand command) {
        if (command == null || command.getEncounterId() == null) {
            InvUpload3505Command empty = new InvUpload3505Command();
            empty.setLines(new ArrayList<>());
            return empty;
        }
        Long encounterId = command.getEncounterId();
        List<MedicationDispense> allRows =
            medicationDispenseQueryService.listUndeletedMedicationDispensesByEncounterId(encounterId);
        List<MedicationDispense> summaryRows = this.filterSummaryPathRows(allRows);
        // Pre：先取全部分发/退药行收费单价，供分桶键「不同单价不合」
        List<Long> allSummaryDispenseIds = new ArrayList<>();
        for (MedicationDispense row : summaryRows) {
            if (row != null && row.getId() != null) {
                allSummaryDispenseIds.add(row.getId());
            }
        }
        Map<Long, ChargeItem> chargeByDispenseIdForBucket =
            dispenseUploadContextLoader.mapChargeItemsByDispenseId(encounterId, allSummaryDispenseIds);
        Map<String, DayMedicationLotBucket> buckets =
            this.buildDayBuckets(summaryRows, chargeByDispenseIdForBucket);
        List<Long> representativeIds = new ArrayList<>();
        for (DayMedicationLotBucket bucket : buckets.values()) {
            if (bucket.getRepresentative() != null && bucket.getRepresentative().getId() != null) {
                representativeIds.add(bucket.getRepresentative().getId());
            }
        }
        InventoryUploadContextLoader.SaleContractContext contracts = dispenseUploadContextLoader.resolveSaleContracts(
            command.getContractBusNo(), command.getContractId(), encounterId, representativeIds);
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        YbSettlementRecord settlement =
            dispenseUploadContextLoader.resolveSettlement(encounterId, contracts.settlementNature());
        Map<Long, ChargeItem> chargeByDispenseId =
            dispenseUploadContextLoader.mapChargeItemsByDispenseId(encounterId, representativeIds);

        InvUpload3505Command request = new InvUpload3505Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contracts.gateway()));
        request.setContractId(contracts.gateway().getId());
        request.setEncounterId(encounterId);
        request.setSourceId(encounterId);
        // 清床汇总径专用来源，便于反结/召回按就诊冲销本批次
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY);

        for (DayMedicationLotBucket bucket : buckets.values()) {
            InvUploadLineCommand line = this.buildNetSaleLine(
                bucket, encounter, contracts.gateway(), contracts.settlementNature(), settlement, chargeByDispenseId);
            if (line == null) {
                continue;
            }
            request.getLines().add(line);
        }
        return request;
    }

    /**
     * 仅保留汇总径发放行（{@code summary_no} 非空）。
     *
     * @param rows 就诊下全部发放行
     * @return 汇总径子集
     */
    private List<MedicationDispense> filterSummaryPathRows(List<MedicationDispense> rows) {
        List<MedicationDispense> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (MedicationDispense row : rows) {
            if (row == null) {
                continue;
            }
            if (isEmpty(row.getSummaryNo())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 按发药日 + 药品 + 批号 + 库位 + 单位 + 收费单价分桶（不同单价不合）。
     *
     * @param summaryRows          汇总径发放行
     * @param chargeByDispenseId   发放行 → 收费项（取单价）
     * @return 分桶表（保序）
     */
    private Map<String, DayMedicationLotBucket> buildDayBuckets(
        List<MedicationDispense> summaryRows,
        Map<Long, ChargeItem> chargeByDispenseId
    ) {
        Map<String, DayMedicationLotBucket> buckets = new LinkedHashMap<>();
        for (MedicationDispense row : summaryRows) {
            LocalDate saleDay = this.resolveSaleDay(row);
            if (saleDay == null || row.getMedicationId() == null) {
                continue;
            }
            String lotKey = row.getLotNumber();
            if (lotKey == null) {
                lotKey = "";
            }
            String unitKey = row.getUnitCode();
            if (unitKey == null) {
                unitKey = "";
            }
            Long locationId = row.getLocationId();
            if (locationId == null) {
                locationId = 0L;
            }
            String priceKey = this.resolveUnitPriceKey(chargeByDispenseId, row.getId());
            String bucketKey = saleDay + "|" + row.getMedicationId() + "|" + lotKey + "|"
                + locationId + "|" + unitKey + "|" + priceKey;
            DayMedicationLotBucket bucket = buckets.get(bucketKey);
            if (bucket == null) {
                bucket = new DayMedicationLotBucket();
                buckets.put(bucketKey, bucket);
            }
            if (this.isForwardCompleted(row)) {
                bucket.addForward(row);
            } else if (this.isRefundCompleted(row)) {
                bucket.addRefund(row);
            }
        }
        return buckets;
    }

    /**
     * 收费单价分桶键；无收费或无单价时用 {@code null} 字面量，避免误合并。
     *
     * @param chargeByDispenseId 收费索引
     * @param dispenseId         发放行主键
     * @return 单价键
     */
    private String resolveUnitPriceKey(Map<Long, ChargeItem> chargeByDispenseId, Long dispenseId) {
        if (chargeByDispenseId == null || dispenseId == null) {
            return "null";
        }
        ChargeItem chargeItem = chargeByDispenseId.get(dispenseId);
        if (chargeItem == null || chargeItem.getUnitPrice() == null) {
            return "null";
        }
        return chargeItem.getUnitPrice().stripTrailingZeros().toPlainString();
    }

    /**
     * 解析销售归属日：优先发药时刻，回落状态变更时刻。
     *
     * @param row 发放行
     * @return 本地日期；均空时 null
     */
    private LocalDate resolveSaleDay(MedicationDispense row) {
        LocalDateTime time = row.getDispenseTime();
        if (time == null) {
            time = row.getStatusChangedTime();
        }
        if (time == null) {
            return null;
        }
        return time.toLocalDate();
    }

    /**
     * 是否为汇总径已核发正向行。
     *
     * @param row 发放行
     * @return true 表示计入正向净量
     */
    private boolean isForwardCompleted(MedicationDispense row) {
        if (!DispenseStatus.COMPLETED.equals(row.getStatusEnum())) {
            return false;
        }
        if (MedicationDispenseType.REFUND.equals(row.getDispenseEnum())) {
            return false;
        }
        return true;
    }

    /**
     * 是否为汇总径已确认退药对冲行。
     *
     * @param row 发放行
     * @return true 表示计入退药扣减
     */
    private boolean isRefundCompleted(MedicationDispense row) {
        if (!DispenseStatus.REFUNDED.equals(row.getStatusEnum())) {
            return false;
        }
        if (!MedicationDispenseType.REFUND.equals(row.getDispenseEnum())) {
            return false;
        }
        return true;
    }

    /**
     * 由分桶净量组装一条 3505 行；净量≤0 时返回 null。
     *
     * @param bucket                   日分桶
     * @param encounter                就诊
     * @param gatewayContract          出站医保契约
     * @param settlementNatureContract 结算性质契约（可自费）
     * @param settlement               结算回执
     * @param chargeByDispenseId       收费项索引
     * @return 净销售行；无可报净量时 null
     */
    private InvUploadLineCommand buildNetSaleLine(
        DayMedicationLotBucket bucket,
        EncounterDetailQueryModel encounter,
        Contract gatewayContract,
        Contract settlementNatureContract,
        YbSettlementRecord settlement,
        Map<Long, ChargeItem> chargeByDispenseId
    ) {
        MedicationDispense representative = bucket.getRepresentative();
        if (representative == null) {
            return null;
        }
        MedicationDefinition medication = medicationDefinitionQueryService
            .findOptionalMedicationDefinitionEntityById(representative.getMedicationId())
            .orElse(null);
        if (medication == null) {
            return null;
        }
        BigDecimal netCnt = BigDecimal.ZERO;
        for (MedicationDispense forward : bucket.getForwards()) {
            BigDecimal lineCnt = InventoryUploadQuantityAssembler.resolveDispenseSelRetnCnt(forward);
            if (lineCnt != null) {
                netCnt = netCnt.add(lineCnt);
            }
        }
        for (MedicationDispense refund : bucket.getRefunds()) {
            BigDecimal lineCnt = InventoryUploadQuantityAssembler.resolveDispenseSelRetnCnt(refund);
            if (lineCnt != null) {
                netCnt = netCnt.subtract(lineCnt);
            }
        }
        // 发退相抵后无净销量：不上报该日该药批
        if (netCnt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        ChargeItem chargeItem = null;
        if (chargeByDispenseId != null && representative.getId() != null) {
            chargeItem = chargeByDispenseId.get(representative.getId());
        }
        InvUploadLineCommand line = dispenseUploadContextLoader.buildLine(
            representative,
            encounter,
            gatewayContract,
            settlementNatureContract,
            settlement,
            chargeItem,
            false,
            null,
            null,
            null);
        // fixmedins_bchno 已由代表发放行 busNo 写入；合并桶内任取一条即可
        line.setSelRetnCnt(netCnt);
        line.setDrugTracCodgs(bucket.resolveNetTraceCodes());
        LocalDateTime lastTime = bucket.resolveLastForwardDispenseTime();
        if (lastTime != null) {
            line.setSelRetnTime(lastTime.format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        return line;
    }

    /**
     * 同一发药日 + 药品 + 批号分桶：累计正向/退药行。
     */
    private static final class DayMedicationLotBucket {

        private final List<MedicationDispense> forwards = new ArrayList<>();
        private final List<MedicationDispense> refunds = new ArrayList<>();

        private void addForward(MedicationDispense row) {
            this.forwards.add(row);
        }

        private void addRefund(MedicationDispense row) {
            this.refunds.add(row);
        }

        private List<MedicationDispense> getForwards() {
            return forwards;
        }

        private List<MedicationDispense> getRefunds() {
            return refunds;
        }

        /**
         * 代表行：首条正向已核发行（药师/处方/批号等字段真源）。
         *
         * @return 代表发放行；无正向时 null
         */
        private MedicationDispense getRepresentative() {
            if (forwards.isEmpty()) {
                return null;
            }
            return forwards.get(0);
        }

        /**
         * 正向追溯码减去退药追溯码，得到净销售应报码列表。
         *
         * @return 净追溯码
         */
        private List<String> resolveNetTraceCodes() {
            List<String> net = new ArrayList<>();
            Set<String> refundSet = new HashSet<>();
            for (MedicationDispense refund : refunds) {
                List<String> codes = ChsTraceCodeExtractAssembler.splitTraceNoCommaString(refund.getTraceNo());
                for (String code : codes) {
                    if (isNotEmpty(code)) {
                        refundSet.add(code.trim());
                    }
                }
            }
            for (MedicationDispense forward : forwards) {
                List<String> codes = ChsTraceCodeExtractAssembler.splitTraceNoCommaString(forward.getTraceNo());
                for (String code : codes) {
                    if (isEmpty(code)) {
                        continue;
                    }
                    String trimmed = code.trim();
                    // 已退药冲销的码不再报销售
                    if (refundSet.contains(trimmed)) {
                        refundSet.remove(trimmed);
                        continue;
                    }
                    net.add(trimmed);
                }
            }
            return net;
        }

        /**
         * 取桶内最晚正向发药时刻作为 sel_retn_time。
         *
         * @return 最晚时刻；无则 null
         */
        private LocalDateTime resolveLastForwardDispenseTime() {
            LocalDateTime latest = null;
            for (MedicationDispense forward : forwards) {
                LocalDateTime time = forward.getDispenseTime();
                if (time == null) {
                    continue;
                }
                if (latest == null || time.isAfter(latest)) {
                    latest = time;
                }
            }
            return latest;
        }
    }
}
