/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.costdetail.assembler;

import com.openhis.health.module.querysupport.nursestation.NurseStationCostDetailReadAssembler;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationCostCategoryTotalDto;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationCostDetailLineDto;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站费用明细/汇总/合计装配（AS-IP-NUR-006 IF-IP-CDT-02 Asm）。
 *
 * <p>职责边界：组套父项 product 解析、service_table→费用大类映射、明细/汇总行与净额合计块 DTO 装配；
 * 不访问库表，产品读模型由 AppService 批量注入。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationCostDetailAssembler {

    /** 汇总数量换算（BR-IP-CDT-011）。 */
    private final NurseStationCostDetailQuantityAssembler nurseStationCostDetailQuantityAssembler;
    private final NurseStationCostDetailReadAssembler nurseStationCostDetailReadAssembler;

    /**
     * 校验候选行均可展示：product_id、category、service_table 归类完整（IF-IP-CDT-02 Pre 步骤 10）。
     *
     * @param rows             域查询候选行
     * @param productByKey     产品读模型复合键索引
     * @param parentChargeById 组套父 ChargeItem 索引
     * @throws IllegalArgumentException 数据不完整时抛出，由 App 映射 EX-IP-CDT-203
     */
    public void validateChargeRowsForAssembly(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                throw new IllegalArgumentException("cost detail row missing charge item");
            }
            ChargeItem charge = row.getChargeItem();
            // 每条 ChargeItem 须可解析展示 product（BR-IP-CDT-007）
            Long displayProductId = this.resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(charge, parentChargeById);
            if (displayProductId == null || StringUtils.isBlank(displayProductTable)) {
                throw new IllegalArgumentException("charge missing display product, chargeItemId=" + charge.getId());
            }
            if (charge.getParentId() != null && parentChargeById != null
                && !parentChargeById.containsKey(charge.getParentId())) {
                throw new IllegalArgumentException("charge missing parent bundle row, chargeItemId=" + charge.getId());
            }
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product == null) {
                throw new IllegalArgumentException("product definition missing, productKey=" + productKey);
            }
            // category_code 须可解析（BR-IP-CDT-009）
            if (StringUtils.isBlank(product.getCategoryCode())) {
                throw new IllegalArgumentException("product category missing, productKey=" + productKey);
            }
            // service_table 须能映射费用大类，未知表名整单失败
            this.resolveExpenseBlockCode(charge.getServiceTable());
        }
    }

    /**
     * 装配明细行（IF-IP-CDT-02；行单价/行合计原样，BR-IP-CDT-012/013）。
     *
     * @param rows             域查询候选行（已按业务时间倒序）
     * @param productByKey     产品读模型索引
     * @param parentChargeById 组套父行索引
     * @return 明细行列表
     */
    public List<NurseStationCostDetailLineDto> buildDetailLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        return nurseStationCostDetailReadAssembler.buildDetailLines(rows, productByKey, parentChargeById);
    }

    /**
     * 装配汇总行（IF-IP-CDT-02；按父项 product_id 分组，块顺序药→诊→材）。
     *
     * @param rows             域查询候选行
     * @param productByKey     产品读模型索引
     * @param parentChargeById 组套父行索引
     * @return 汇总行列表
     */
    public List<NurseStationCostSummaryLineDto> buildSummaryLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        List<NurseStationCostSummaryLineDto> lines = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return lines;
        }
        Map<String, List<InpatientNurseStationCostDetailChargeRow>> grouped = new LinkedHashMap<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long displayProductId = this.resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(charge, parentChargeById);
            String groupKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            List<InpatientNurseStationCostDetailChargeRow> bucket = grouped.get(groupKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(groupKey, bucket);
            }
            bucket.add(row);
        }
        for (Map.Entry<String, List<InpatientNurseStationCostDetailChargeRow>> entry : grouped.entrySet()) {
            List<InpatientNurseStationCostDetailChargeRow> groupRows = entry.getValue();
            if (groupRows.isEmpty()) {
                continue;
            }
            ChargeItem sampleCharge = groupRows.get(0).getChargeItem();
            Long displayProductId = this.resolveDisplayProductId(sampleCharge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(sampleCharge, parentChargeById);
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            BigDecimal lineTotal = BigDecimal.ZERO;
            BigDecimal sampleUnitPrice = null;
            String expenseBlockCode = this.resolveExpenseBlockCode(sampleCharge.getServiceTable());
            for (InpatientNurseStationCostDetailChargeRow groupRow : groupRows) {
                ChargeItem charge = groupRow.getChargeItem();
                if (charge.getTotalPrice() != null) {
                    lineTotal = lineTotal.add(charge.getTotalPrice());
                }
                if (sampleUnitPrice == null && charge.getUnitPrice() != null) {
                    sampleUnitPrice = charge.getUnitPrice();
                }
            }
            NurseStationCostSummaryLineDto summaryLine = new NurseStationCostSummaryLineDto();
            summaryLine.setProductId(displayProductId);
            summaryLine.setProductTableName(displayProductTable);
            if (product != null) {
                summaryLine.setItemName(product.getProductName());
                summaryLine.setItemSpec(product.getTotalVolume());
                summaryLine.setCategoryCode(product.getCategoryCode());
                summaryLine.setCategoryDisplay(product.getCategoryCodeDictText());
                summaryLine.setPackageUnitCode(product.getPackageUnitCode());
                summaryLine.setPackageUnitDisplay(product.getPackageUnitCodeDictText());
                summaryLine.setSplitUnitCode(product.getSplitUnitCode());
                summaryLine.setSplitUnitDisplay(product.getSplitUnitCodeDictText());
            }
            summaryLine.setUnitPrice(sampleUnitPrice);
            summaryLine.setLineTotalAmount(lineTotal);
            NurseStationCostDetailQuantityAssembler.PackageSplitQuantityResult quantityResult =
                nurseStationCostDetailQuantityAssembler.aggregatePackageAndSplitQuantity(groupRows, product);
            summaryLine.setPackageQuantity(quantityResult.packageQuantity());
            summaryLine.setSplitUnitQuantity(quantityResult.splitUnitQuantity());
            summaryLine.setExpenseBlockCode(expenseBlockCode);
            summaryLine.setExpenseBlockDisplay(this.resolveExpenseBlockDisplay(expenseBlockCode));
            lines.add(summaryLine);
        }
        // 汇总块顺序：药品 → 诊疗 → 耗材（BR-IP-CDT-008）
        lines.sort(Comparator.comparingInt(line -> this.expenseBlockSortOrder(line.getExpenseBlockCode())));
        return lines;
    }

    /**
     * 装配合计块（IF-IP-CDT-02；total_price 净额，BR-IP-CDT-010）。
     *
     * @param rows             域查询候选行
     * @param productByKey     产品读模型索引
     * @param parentChargeById 组套父行索引
     * @return 合计块
     */
    public NurseStationCostDetailTotalsDto buildTotals(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        NurseStationCostDetailTotalsDto totals = new NurseStationCostDetailTotalsDto();
        totals.setMedicationTotal(BigDecimal.ZERO);
        totals.setServiceTotal(BigDecimal.ZERO);
        totals.setDeviceTotal(BigDecimal.ZERO);
        totals.setGrandTotal(BigDecimal.ZERO);
        Map<String, NurseStationCostCategoryTotalDto> categoryAccumulator = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            totals.setCategoryTotals(new ArrayList<>());
            return totals;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            BigDecimal lineAmount = charge.getTotalPrice();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            String expenseBlockCode = this.resolveExpenseBlockCode(charge.getServiceTable());
            // 按费用大类累加净额（含退费负金额，BR-IP-CDT-010）
            if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION.equals(expenseBlockCode)) {
                totals.setMedicationTotal(totals.getMedicationTotal().add(lineAmount));
            } else if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE.equals(expenseBlockCode)) {
                totals.setServiceTotal(totals.getServiceTotal().add(lineAmount));
            } else if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE.equals(expenseBlockCode)) {
                totals.setDeviceTotal(totals.getDeviceTotal().add(lineAmount));
            }
            totals.setGrandTotal(totals.getGrandTotal().add(lineAmount));
            Long displayProductId = this.resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(charge, parentChargeById);
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product == null || StringUtils.isBlank(product.getCategoryCode())) {
                continue;
            }
            String categoryCode = product.getCategoryCode().trim();
            NurseStationCostCategoryTotalDto categoryTotal = categoryAccumulator.get(categoryCode);
            if (categoryTotal == null) {
                categoryTotal = new NurseStationCostCategoryTotalDto();
                categoryTotal.setCategoryCode(categoryCode);
                categoryTotal.setCategoryDisplay(product.getCategoryCodeDictText());
                categoryTotal.setAmount(BigDecimal.ZERO);
                categoryAccumulator.put(categoryCode, categoryTotal);
            }
            categoryTotal.setAmount(categoryTotal.getAmount().add(lineAmount));
        }
        totals.setCategoryTotals(new ArrayList<>(categoryAccumulator.values()));
        return totals;
    }

    /**
     * 组套展示用产品 id：子项取父项 product_id（BR-IP-CDT-006）。
     *
     * @param charge           当前收费行
     * @param parentChargeById 父行索引
     * @return 展示用产品 id
     */
    public Long resolveDisplayProductId(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        return nurseStationCostDetailReadAssembler.resolveDisplayProductId(charge, parentChargeById);
    }

    /**
     * 组套展示用产品表名：子项取父项 product_table（BR-IP-CDT-006）。
     *
     * @param charge           当前收费行
     * @param parentChargeById 父行索引
     * @return 展示用产品表名
     */
    public String resolveDisplayProductTable(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        return nurseStationCostDetailReadAssembler.resolveDisplayProductTable(charge, parentChargeById);
    }

    /**
     * 将 service_table 映射为费用大类块 code（BR-IP-CDT-008）。
     *
     * <p>识别：药品 {@code med_medication_request}/{@code med_medication_dispense}；
     * 诊疗 {@code wkf_service_request}；耗材 {@code wkf_device_request}/{@code wkf_device_dispense}。</p>
     *
     * @param serviceTable 上游业务表名
     * @return MEDICATION / SERVICE / DEVICE
     * @throws IllegalArgumentException 未识别表名
     */
    public String resolveExpenseBlockCode(String serviceTable) {
        return nurseStationCostDetailReadAssembler.resolveExpenseBlockCode(serviceTable);
    }

    /**
     * 费用大类块展示名。
     *
     * @param expenseBlockCode 块 code
     * @return 展示名
     */
    private String resolveExpenseBlockDisplay(String expenseBlockCode) {
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION.equals(expenseBlockCode)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION_DISPLAY;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE.equals(expenseBlockCode)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE_DISPLAY;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE.equals(expenseBlockCode)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE_DISPLAY;
        }
        return expenseBlockCode;
    }

    /**
     * 费用大类块排序权重（药→诊→材）。
     *
     * @param expenseBlockCode 块 code
     * @return 排序序号
     */
    private int expenseBlockSortOrder(String expenseBlockCode) {
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION.equals(expenseBlockCode)) {
            return 0;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE.equals(expenseBlockCode)) {
            return 1;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE.equals(expenseBlockCode)) {
            return 2;
        }
        return 99;
    }
}
