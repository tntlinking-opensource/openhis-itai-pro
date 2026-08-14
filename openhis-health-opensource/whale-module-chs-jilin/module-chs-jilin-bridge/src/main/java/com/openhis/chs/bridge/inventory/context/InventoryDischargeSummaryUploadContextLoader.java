/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDischargeSummarySalesCommand;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 住院清床（或无床直结）后净销售 3505 组装。
 *
 * <p>为何挂清床/无床直结：住院发药当时尚无结算 {@code setl_id}；结算完成后才有稳定结算上下文。
 * 全住院期间发放行发退相抵后，按<strong>药品 + 批号 + 包装锚单价</strong>分桶合并（不含库位、不按天）；
 * 追溯码去重；{@code trdn_flag} 按码累计 OUT−IN 实扣判定。只读
 * {@code MedicationDispense.trace_no}（汇总径须二次分配落回发放表后方可上传）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryDischargeSummaryUploadContextLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final EncounterQueryService encounterQueryService;
    private final InventoryUploadContextLoader dispenseUploadContextLoader;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;
    private final ChsInsuranceContractReaderImpl contractReader;

    /**
     * 组装住院清床/无床直结净销售 3505 命令；无净量行时返回空 lines。
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
        // Pre：纳入全部已核发正向与已确认退药（按患者发 + 汇总发，不滤 summary_no）
        List<MedicationDispense> saleRows = this.filterSalePathRows(allRows);
        List<Long> allDispenseIds = new ArrayList<>();
        Set<Long> medicationIds = new HashSet<>();
        for (MedicationDispense row : saleRows) {
            if (row == null) {
                continue;
            }
            if (row.getId() != null) {
                allDispenseIds.add(row.getId());
            }
            if (row.getMedicationId() != null) {
                medicationIds.add(row.getMedicationId());
            }
        }
        Map<Long, MedicationDefinition> medicationById = this.mapMedicationsById(medicationIds);
        Map<Long, ChargeItem> chargeByDispenseId =
            dispenseUploadContextLoader.mapChargeItemsByDispenseId(encounterId, allDispenseIds);
        Map<String, MedicationLotPriceBucket> buckets =
            this.buildMergeBuckets(saleRows, chargeByDispenseId, medicationById);
        List<Long> representativeIds = new ArrayList<>();
        for (MedicationLotPriceBucket bucket : buckets.values()) {
            if (bucket.getRepresentative() != null && bucket.getRepresentative().getId() != null) {
                representativeIds.add(bucket.getRepresentative().getId());
            }
        }
        InventoryUploadContextLoader.SaleContractContext contracts = dispenseUploadContextLoader.resolveSaleContracts(
            command.getContractBusNo(), command.getContractId(), encounterId, representativeIds);
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        // Pre：住院结算回执须按 IMP 取 setl_id（禁止误查 AMB）
        YbSettlementRecord settlement = dispenseUploadContextLoader.resolveSettlement(
            encounterId, contracts.settlementNature(), ChsSettleEncounterClass.IMP);

        InvUpload3505Command request = new InvUpload3505Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contracts.gateway()));
        request.setContractId(contracts.gateway().getId());
        request.setEncounterId(encounterId);
        request.setSourceId(encounterId);
        // 清床/无床直结专用来源，便于反结/召回按就诊冲销本批次
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY);

        LocalDateTime saleTime = LocalDateTime.now();
        for (MedicationLotPriceBucket bucket : buckets.values()) {
            InvUploadLineCommand line = this.buildNetSaleLine(
                bucket,
                encounter,
                contracts.gateway(),
                contracts.settlementNature(),
                settlement,
                chargeByDispenseId,
                medicationById,
                saleTime);
            if (line == null) {
                continue;
            }
            request.getLines().add(line);
        }
        return request;
    }

    /**
     * 保留已核发正向与已确认退药对冲行（发放表为上传真源）。
     *
     * @param rows 就诊下全部发放行
     * @return 可计入净销售的子集
     */
    private List<MedicationDispense> filterSalePathRows(List<MedicationDispense> rows) {
        List<MedicationDispense> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (MedicationDispense row : rows) {
            if (row == null) {
                continue;
            }
            // 已核发正向：计入加项
            if (this.isForwardCompleted(row)) {
                result.add(row);
                continue;
            }
            // 已确认退药：计入减项
            if (this.isRefundCompleted(row)) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 批量加载药品定义，供包装锚单价与单位换算。
     *
     * @param medicationIds 药品主键集合
     * @return id → 定义
     */
    private Map<Long, MedicationDefinition> mapMedicationsById(Set<Long> medicationIds) {
        Map<Long, MedicationDefinition> result = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()) {
            return result;
        }
        List<MedicationDefinition> rows = medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            result.put(row.getId(), row);
        }
        return result;
    }

    /**
     * 按药品 + 批号 + 包装锚单价分桶（不同批/不同价不合；不含库位、不按天）。
     *
     * @param saleRows             销售相关发放行
     * @param chargeByDispenseId   发放行 → 收费项
     * @param medicationById       药品索引
     * @return 分桶表（保序）
     */
    private Map<String, MedicationLotPriceBucket> buildMergeBuckets(
        List<MedicationDispense> saleRows,
        Map<Long, ChargeItem> chargeByDispenseId,
        Map<Long, MedicationDefinition> medicationById
    ) {
        Map<String, MedicationLotPriceBucket> buckets = new LinkedHashMap<>();
        for (MedicationDispense row : saleRows) {
            if (row.getMedicationId() == null) {
                continue;
            }
            MedicationDefinition medication = medicationById.get(row.getMedicationId());
            String lotKey = row.getLotNumber();
            if (lotKey == null) {
                lotKey = "";
            }
            String priceKey = this.resolvePackageAnchoredPriceKey(
                medication, chargeByDispenseId.get(row.getId()));
            String bucketKey = row.getMedicationId() + "|" + lotKey + "|" + priceKey;
            MedicationLotPriceBucket bucket = buckets.get(bucketKey);
            if (bucket == null) {
                bucket = new MedicationLotPriceBucket();
                buckets.put(bucketKey, bucket);
            }
            // 桶内记住包装锚单价，供上报 finl_trns_pric
            if (bucket.getPackageUnitPrice() == null && !"null".equals(priceKey)) {
                bucket.setPackageUnitPrice(new BigDecimal(priceKey));
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
     * 收费单价换算到包装单位后作为分桶键；无价时 {@code null} 字面量避免误合并。
     *
     * @param medication 药品；可空
     * @param chargeItem 收费项；可空
     * @return 包装锚单价键
     */
    private String resolvePackageAnchoredPriceKey(MedicationDefinition medication, ChargeItem chargeItem) {
        if (chargeItem == null || chargeItem.getUnitPrice() == null) {
            return "null";
        }
        BigDecimal packagePrice = chargeItem.getUnitPrice();
        // 有药品与收费单位：将收费单价换到包装单位再比价
        if (medication != null && isNotEmpty(chargeItem.getQuantityUnit())) {
            String packageUnit = medication.getPackageUnitCode();
            if (isNotEmpty(packageUnit)) {
                BigDecimal converted = InventoryUploadQuantityAssembler.convertUnitPricePackageAnchored(
                    medication,
                    chargeItem.getQuantityUnit(),
                    chargeItem.getUnitPrice(),
                    packageUnit);
                if (converted != null) {
                    packagePrice = converted;
                }
            }
        }
        return MonetaryAmountSupport.scaleCalc(packagePrice).stripTrailingZeros().toPlainString();
    }

    /**
     * 是否为已核发正向行。
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
     * 是否为已确认退药对冲行。
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
     * @param bucket                   合并桶
     * @param encounter                就诊
     * @param gatewayContract          出站医保契约
     * @param settlementNatureContract 结算性质契约
     * @param settlement               住院结算回执
     * @param chargeByDispenseId       收费项索引
     * @param medicationById           药品索引
     * @param saleTime                 销售认定时刻（清床/无床直结触发时）
     * @return 净销售行；无可报净量时 null
     */
    private InvUploadLineCommand buildNetSaleLine(
        MedicationLotPriceBucket bucket,
        EncounterDetailQueryModel encounter,
        Contract gatewayContract,
        Contract settlementNatureContract,
        YbSettlementRecord settlement,
        Map<Long, ChargeItem> chargeByDispenseId,
        Map<Long, MedicationDefinition> medicationById,
        LocalDateTime saleTime
    ) {
        MedicationDispense representative = bucket.getRepresentative();
        if (representative == null) {
            return null;
        }
        MedicationDefinition medication = medicationById.get(representative.getMedicationId());
        if (medication == null) {
            return null;
        }
        String reportUnit = this.resolveReportUnitCode(medication, representative);
        BigDecimal netCnt = this.resolveNetQuantityInReportUnit(bucket, medication, reportUnit);
        // 发退相抵后无净销量：不上报该药批价
        if (netCnt == null || netCnt.compareTo(BigDecimal.ZERO) <= 0) {
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
        List<String> netCodes = bucket.resolveDistinctNetTraceCodes();
        line.setSelRetnCnt(netCnt);
        line.setDrugTracCodgs(netCodes);
        // 销售认定时点：清床/无床直结触发时刻（非各发药日）
        if (saleTime != null) {
            line.setSelRetnTime(saleTime.format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        // 单价：包装锚价换到与数量同阶的上报单位
        BigDecimal packagePrice = bucket.getPackageUnitPrice();
        if (packagePrice != null) {
            BigDecimal reportPrice = InventoryUploadQuantityAssembler.convertUnitPricePackageAnchored(
                medication,
                medication.getPackageUnitCode(),
                packagePrice,
                reportUnit);
            if (reportPrice != null) {
                line.setFinlTrnsPric(reportPrice);
            } else {
                line.setFinlTrnsPric(MonetaryAmountSupport.scaleCalc(packagePrice));
            }
        }
        // Cmd：按码累计 OUT−IN 重算 trdn_flag（覆盖代表行单 busNo 算法）
        uploadTraceLineSupport.applyClearBedMergedTrdnFlagAndPolicy(
            medication,
            reportUnit,
            netCodes,
            bucket.listForwardBusNos(),
            bucket.listRefundBusNos(),
            line);
        return line;
    }

    /**
     * 上报数量单位：优先包装单位，否则代表行单位。
     *
     * @param medication     药品
     * @param representative 代表发放行
     * @return 单位 code
     */
    private String resolveReportUnitCode(MedicationDefinition medication, MedicationDispense representative) {
        if (medication != null && isNotEmpty(medication.getPackageUnitCode())) {
            return medication.getPackageUnitCode().trim();
        }
        if (representative != null && isNotEmpty(representative.getUnitCode())) {
            return representative.getUnitCode().trim();
        }
        return null;
    }

    /**
     * 桶内发退数量统一换算到上报单位后相抵。
     *
     * @param bucket     合并桶
     * @param medication 药品
     * @param reportUnit 上报单位
     * @return 净数量；无法得到正净量时 null 或 ≤0
     */
    private BigDecimal resolveNetQuantityInReportUnit(
        MedicationLotPriceBucket bucket,
        MedicationDefinition medication,
        String reportUnit
    ) {
        BigDecimal net = BigDecimal.ZERO;
        for (MedicationDispense forward : bucket.getForwards()) {
            BigDecimal lineCnt = this.toReportUnitQuantity(medication, forward, reportUnit);
            if (lineCnt != null) {
                net = net.add(lineCnt);
            }
        }
        for (MedicationDispense refund : bucket.getRefunds()) {
            BigDecimal lineCnt = this.toReportUnitQuantity(medication, refund, reportUnit);
            if (lineCnt != null) {
                net = net.subtract(lineCnt);
            }
        }
        return net;
    }

    /**
     * 单行发放数量换算到上报单位：先到拆零再落到目标单位。
     *
     * @param medication 药品
     * @param row        发放行
     * @param reportUnit 上报单位
     * @return 上报单位数量
     */
    private BigDecimal toReportUnitQuantity(
        MedicationDefinition medication,
        MedicationDispense row,
        String reportUnit
    ) {
        BigDecimal raw = InventoryUploadQuantityAssembler.resolveDispenseSelRetnCnt(row);
        if (raw == null) {
            return null;
        }
        // 缺药品或单位：无法换算，原样（同单位场景）
        if (medication == null || isEmpty(reportUnit) || isEmpty(row.getUnitCode())) {
            return raw;
        }
        // 单位已一致：不换算
        if (reportUnit.trim().equals(row.getUnitCode().trim())) {
            return raw;
        }
        BigDecimal split = InventoryUploadQuantityAssembler.operationQuantityToSplit(
            medication, row.getUnitCode(), raw);
        if (split == null) {
            return raw;
        }
        BigDecimal converted = InventoryUploadQuantityAssembler.splitQuantityToOperationUnit(
            medication, reportUnit, split);
        if (converted == null) {
            return raw;
        }
        return converted;
    }

    /**
     * 同一药品 + 批号 + 包装锚单价桶：累计正向/退药行。
     */
    private static final class MedicationLotPriceBucket {

        private final List<MedicationDispense> forwards = new ArrayList<>();
        private final List<MedicationDispense> refunds = new ArrayList<>();
        /** 包装单位单价（分桶键真源）。 */
        private BigDecimal packageUnitPrice;

        /**
         * 记入正向已核发行。
         *
         * @param row 发放行
         */
        private void addForward(MedicationDispense row) {
            this.forwards.add(row);
        }

        /**
         * 记入退药对冲行。
         *
         * @param row 发放行
         */
        private void addRefund(MedicationDispense row) {
            this.refunds.add(row);
        }

        /**
         * 桶内正向已核发行（用于净量加项与 OUT 实扣汇总）。
         *
         * @return 正向行列表（可为空列表）
         */
        private List<MedicationDispense> getForwards() {
            return forwards;
        }

        /**
         * 桶内已确认退药对冲行（用于净量减项与 IN 实扣汇总）。
         *
         * @return 退药行列表（可为空列表）
         */
        private List<MedicationDispense> getRefunds() {
            return refunds;
        }

        /**
         * 本桶包装单位单价（分桶键与 {@code finl_trns_pric} 真源）。
         *
         * @return 包装锚单价；无收费价时 null
         */
        private BigDecimal getPackageUnitPrice() {
            return packageUnitPrice;
        }

        /**
         * 写入包装锚单价（仅首次非空）。
         *
         * @param packageUnitPrice 包装单位单价
         */
        private void setPackageUnitPrice(BigDecimal packageUnitPrice) {
            this.packageUnitPrice = packageUnitPrice;
        }

        /**
         * 代表行：首条正向已核发行（药师/处方等字段真源）。
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
         * 正向追溯码多重集减去退药码后去重，得到净销售 drugtracinfo。
         *
         * <p>同码多天扫只保留一次；部分退按出现次数冲销，冲光则不再上报该码。</p>
         *
         * @return 去重净追溯码（保序）
         */
        private List<String> resolveDistinctNetTraceCodes() {
            Map<String, Integer> forwardCounts = new LinkedHashMap<>();
            for (MedicationDispense forward : forwards) {
                List<String> codes = ChsTraceCodeExtractAssembler.splitTraceNoCommaString(forward.getTraceNo());
                for (String code : codes) {
                    if (isEmpty(code)) {
                        continue;
                    }
                    String trimmed = code.trim();
                    Integer count = forwardCounts.get(trimmed);
                    if (count == null) {
                        forwardCounts.put(trimmed, 1);
                    } else {
                        forwardCounts.put(trimmed, count + 1);
                    }
                }
            }
            for (MedicationDispense refund : refunds) {
                List<String> codes = ChsTraceCodeExtractAssembler.splitTraceNoCommaString(refund.getTraceNo());
                for (String code : codes) {
                    if (isEmpty(code)) {
                        continue;
                    }
                    String trimmed = code.trim();
                    Integer count = forwardCounts.get(trimmed);
                    // 退药码不在正向集合：忽略
                    if (count == null) {
                        continue;
                    }
                    // 冲销一次出现
                    if (count <= 1) {
                        forwardCounts.remove(trimmed);
                    } else {
                        forwardCounts.put(trimmed, count - 1);
                    }
                }
            }
            return new ArrayList<>(forwardCounts.keySet());
        }

        /**
         * 正向发放 bus_no 列表（去重保序），供按码汇总 OUT。
         *
         * @return bus_no 集合
         */
        private List<String> listForwardBusNos() {
            Set<String> busNos = new LinkedHashSet<>();
            for (MedicationDispense forward : forwards) {
                if (forward != null && isNotEmpty(forward.getBusNo())) {
                    busNos.add(forward.getBusNo().trim());
                }
            }
            return new ArrayList<>(busNos);
        }

        /**
         * 退药发放 bus_no 列表（去重保序），供按码汇总 IN。
         *
         * @return bus_no 集合
         */
        private List<String> listRefundBusNos() {
            Set<String> busNos = new LinkedHashSet<>();
            for (MedicationDispense refund : refunds) {
                if (refund != null && isNotEmpty(refund.getBusNo())) {
                    busNos.add(refund.getBusNo().trim());
                }
            }
            return new ArrayList<>(busNos);
        }
    }
}
