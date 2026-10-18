/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.costdetail.assembler;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.NurseStationCostDetailReadAssembler;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostCategoryTotalDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailLineDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailTotalsDto;
import com.zxplat.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryActivityLineDto;
import com.zxplat.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站费用明细/汇总/合计装配（AS-IP-NUR-006 IF-IP-CDT-02 Asm）。
 *
 * <p>职责边界：带子项诊疗父账单 product 解析、service_table→费用大类映射、明细/汇总行与净额合计块 DTO 装配；
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
     * @param parentChargeById 父账单 ChargeItem 索引
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
            if (displayProductId == null || StringUtils.isEmpty(displayProductTable)) {
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
            if (StringUtils.isEmpty(product.getCategoryCode())) {
                throw new IllegalArgumentException("product category missing, productKey=" + productKey);
            }
            // service_table 须能映射费用大类，未知表名整单失败
            this.resolveExpenseBlockCode(charge.getServiceTable());
        }
    }

    /**
     * 按项目关键字过滤收费候选行（BR-IP-CDT-019）。
     *
     * <p>关键字空白则原样返回；否则对展示用产品的 {@code productName}、{@code productBusNo}
     * 做忽略大小写的包含匹配，命中任一即保留。子项账单按父项产品匹配。</p>
     *
     * @param rows             域查询候选行（校验通过后）
     * @param productByKey     产品读模型复合键索引
     * @param parentChargeById 父账单 ChargeItem 索引
     * @param keyword          可选关键字；空白不过滤
     * @return 过滤后的候选行（保持原顺序）
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterChargeRowsByItemKeyword(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById,
        String keyword) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        // 未填关键字：不过滤，明细/汇总/合计仍覆盖日期区间内全部合法行
        if (StringUtils.isEmpty(normalizedKeyword)) {
            return rows;
        }
        List<InpatientNurseStationCostDetailChargeRow> filtered = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long displayProductId = this.resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(charge, parentChargeById);
            // 缺展示产品键时跳过：上游校验已保证正常行可解析，此处仅防空指针
            if (displayProductId == null || StringUtils.isEmpty(displayProductTable)) {
                continue;
            }
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (this.matchesItemKeyword(product, normalizedKeyword)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * 费用明细读侧排除子项账单行（父行展示/合计；子行不参与回显）。
     *
     * @param rows 域查询候选行
     * @return 仅父账单及无 parent_id 的普通行
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterOutPackageChildChargeRows(
        List<InpatientNurseStationCostDetailChargeRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<InpatientNurseStationCostDetailChargeRow> filtered = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            // 子账单挂 parent_id：不进护士站费用明细/汇总，避免与父行双计
            if (row.getChargeItem().getParentId() != null) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 业务打印：仅检验带子项出子账单、隐藏对应父账单；检查/治疗仍出父项。
     * 组套等其它 {@code parent_id} 仍过滤掉。
     *
     * @param rows             域查询候选行（含父账单与子账单）
     * @param productByKey     产品读模型复合键索引
     * @param parentChargeById 父账单 ChargeItem 索引
     * @return 打印用行：检验子账单 + 非检验父行 + 无活动定义子项的普通行
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterChargeRowsForActivityDefinitionChildrenPrint(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        if (rows == null || rows.isEmpty()) {
            // 无候选行则打印清单为空
            return new ArrayList<>();
        }
        Map<Long, ChargeItem> chargeById = new HashMap<>();
        if (parentChargeById != null && !parentChargeById.isEmpty()) {
            // 父账单索引并入本批，便于取父产品类目
            chargeById.putAll(parentChargeById);
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
                // 无主键无法按 id 取父账单
                continue;
            }
            chargeById.put(row.getChargeItem().getId(), row.getChargeItem());
        }
        LinkedHashSet<Long> labActivityDefinitionParentIds = new LinkedHashSet<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                // 空行不参与检验父集合
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getParentId() == null) {
                // 无 parent_id 不是带子项子账单
                continue;
            }
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getProductTable())) {
                // 组套等其它子账单不计入检验展开父集合
                continue;
            }
            ChargeItem parentCharge = chargeById.get(chargeItem.getParentId());
            if (!this.isLabChargeProduct(parentCharge, productByKey)) {
                // 检查/治疗父项仍出父账单
                continue;
            }
            labActivityDefinitionParentIds.add(chargeItem.getParentId());
        }
        List<InpatientNurseStationCostDetailChargeRow> filtered = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                // 空行不进打印清单
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getId() != null && labActivityDefinitionParentIds.contains(chargeItem.getId())) {
                // 检验带子项父账单不进打印清单
                continue;
            }
            if (chargeItem.getParentId() != null
                && !labActivityDefinitionParentIds.contains(chargeItem.getParentId())) {
                // 检查/治疗子账单与组套子账单仍不进清单
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 收费项产品类目是否为检验（code 22 / 枚举名 PROOF）。
     *
     * @param charge       收费项；可空
     * @param productByKey 产品读模型复合键索引
     * @return 检验为 true
     */
    private boolean isLabChargeProduct(
        ChargeItem charge, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (charge == null || charge.getProductId() == null || StringUtils.isEmpty(charge.getProductTable())) {
            // 无父账单或无产品锚点则无法判定检验
            return false;
        }
        if (productByKey == null || productByKey.isEmpty()) {
            // 无产品目录则无法判定检验
            return false;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(charge.getProductTable(), charge.getProductId());
        ProductDefinitionDisplayDto product = productByKey.get(key);
        if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
            // 父产品缺失或无类目则不按检验展开
            return false;
        }
        String categoryCode = product.getCategoryCode().trim();
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF.equals(categoryCode)) {
            // 落库类目 code 为检验 22
            return true;
        }
        if (ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_PROOF.equals(categoryCode)) {
            // 兼容枚举名 PROOF
            return true;
        }
        return false;
    }

    /**
     * 费用明细读侧排除嘱托活动账单行（BR-IP-CDT-020 / BR-IP-ORD-028⑤）。
     *
     * <p>识别双锚：展示产品 {@code category_code=52} 或产品名精确「嘱托」。
     * 防御存量误建 Charge；新执行写路径已禁止建账（BR-IP-EXE-033）。</p>
     *
     * @param rows             域查询候选行
     * @param productByKey     产品读模型复合键索引
     * @param parentChargeById 父账单 ChargeItem 索引
     * @return 排除嘱托后的候选行（保持原顺序）
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterOutEntrustChargeRows(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<InpatientNurseStationCostDetailChargeRow> filtered = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long displayProductId = this.resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = this.resolveDisplayProductTable(charge, parentChargeById);
            if (displayProductId == null || StringUtils.isEmpty(displayProductTable)) {
                // 缺产品键：保留由上游 validate 拦截，此处不擅自丢弃非嘱托行
                filtered.add(row);
                continue;
            }
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            // 嘱托不计费：费用清单不展示（BR-IP-CDT-020）
            if (this.isEntrustProduct(product)) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 费用清单打印：成对剔除冲销留痕（原行 + 冲销负向行）。
     *
     * <p>{@code refund_id} 双义：仅 {@link ChargeItem#isPricingOffsetLine()}（数量非正）视为冲销留痕；
     * 反结/半退再生正向行虽有 {@code refund_id} 仍保留可打印。剔除集合 = 窗内冲销负向行及其指向的原行
     * + 调用方根据窗外冲销负向行反查得到的已被冲销原行主键。</p>
     *
     * @param rows                      候选收费行
     * @param offsetSourceIdsOutsideWin 日期窗外冲销负向行指向的原行主键（可空）
     * @return 剔除成对留痕后的行
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterOutOffsetPairedChargeRows(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Set<Long> offsetSourceIdsOutsideWin) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 成对剔除 id：冲销负向行自身、其 refund_id 原行、窗外冲销反查到的原行
        Set<Long> excludeIds = new LinkedHashSet<>();
        if (offsetSourceIdsOutsideWin != null && !offsetSourceIdsOutsideWin.isEmpty()) {
            excludeIds.addAll(offsetSourceIdsOutsideWin);
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            // 反结再生正向（refund_id 有值但数量为正）不是冲销留痕，禁止成对剔除
            if (!charge.isPricingOffsetLine()) {
                continue;
            }
            // 冲销负向行与被冲销原行一并剔除
            if (charge.getId() != null) {
                excludeIds.add(charge.getId());
            }
            excludeIds.add(charge.getRefundId());
        }
        if (excludeIds.isEmpty()) {
            return rows;
        }
        List<InpatientNurseStationCostDetailChargeRow> filtered = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
                continue;
            }
            // 保留未被成对冲销的有效费用行（含反结再生正向）
            if (!excludeIds.contains(row.getChargeItem().getId())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * 展示产品是否为嘱托活动（类目 52 或名称「嘱托」）。
     *
     * @param product 产品读模型；可 null
     * @return true 表示嘱托，须从费用清单排除
     */
    private boolean isEntrustProduct(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return false;
        }
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST.equals(
            StringUtils.trimToNull(product.getCategoryCode()))) {
            return true;
        }
        return GeneralSharedConstants.ENTRUST_ACTIVITY_NAME.equals(
            StringUtils.trimToNull(product.getProductName()));
    }

    /**
     * 产品名称或院内编码是否包含已归一化关键字（忽略大小写）。
     *
     * @param product           展示用产品读模型
     * @param normalizedKeyword 已 trim 并转小写的关键字
     * @return 命中名称或编码任一则为 true
     */
    private boolean matchesItemKeyword(ProductDefinitionDisplayDto product, String normalizedKeyword) {
        if (product == null) {
            return false;
        }
        String productName = StringUtils.defaultString(product.getProductName()).toLowerCase(Locale.ROOT);
        if (productName.contains(normalizedKeyword)) {
            return true;
        }
        String productBusNo = StringUtils.defaultString(product.getProductBusNo()).toLowerCase(Locale.ROOT);
        return productBusNo.contains(normalizedKeyword);
    }

    /**
     * 装配明细行（IF-IP-CDT-02；行单价/行合计原样，BR-IP-CDT-012/013）。
     *
     * @param rows             域查询候选行（已按业务时间倒序）
     * @param productByKey     产品读模型索引
     * @param parentChargeById 父账单索引
     * @return 明细行列表
     */
    public List<NurseStationCostDetailLineDto> buildDetailLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        return nurseStationCostDetailReadAssembler.buildDetailLines(
            rows, productByKey, parentChargeById, Collections.emptySet());
    }

    /**
     * 装配明细行（IF-IP-CDT-02；行单价/行合计原样，BR-IP-CDT-012/013）。
     *
     * @param rows                      域查询候选行（已按业务时间倒序）
     * @param productByKey              产品读模型索引
     * @param parentChargeById          父账单索引
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合（generate_source=auto-roll-fees）
     * @return 明细行列表
     */
    public List<NurseStationCostDetailLineDto> buildDetailLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById,
        Set<Long> autoRollServiceRequestIds) {
        return nurseStationCostDetailReadAssembler.buildDetailLines(
            rows, productByKey, parentChargeById, autoRollServiceRequestIds);
    }

    /**
     * 装配汇总行（IF-IP-CDT-02；按父项 product_id 分组，块顺序药→诊→材）。
     *
     * @param rows                  域查询候选行
     * @param productByKey          产品读模型索引
     * @param parentChargeById      父账单索引
     * @param practitionerNameById  操作人 id → 展示名（汇总展开明细用）
     * @return 汇总行列表
     */
    public List<NurseStationCostSummaryLineDto> buildSummaryLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById,
        Map<Long, String> practitionerNameById) {
        return this.buildSummaryLines(
            rows, productByKey, parentChargeById, practitionerNameById, Map.of(), Collections.emptySet());
    }

    /**
     * 装配汇总行（IF-IP-CDT-02；含自动计费活动类型识别）。
     *
     * @param rows                      域查询候选行
     * @param productByKey              产品读模型索引
     * @param parentChargeById          父账单索引
     * @param practitionerNameById      操作人 id → 展示名
     * @param operatorByChargeItemId    chargeItemId → 操作人（Procedure / DeviceDispense）
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合
     * @return 汇总行列表
     */
    public List<NurseStationCostSummaryLineDto> buildSummaryLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById,
        Map<Long, String> practitionerNameById,
        Map<Long, Long> operatorByChargeItemId,
        Set<Long> autoRollServiceRequestIds) {
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
                // 医保等级：目录对照回填，汇总表与明细/打印同源
                summaryLine.setChrgitmLv(product.getChrgitmLv());
                summaryLine.setChrgitmLvText(product.getChrgitmLvText());
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
            summaryLine.setActivityLines(this.buildSummaryActivityLines(
                groupRows, product, practitionerNameById, operatorByChargeItemId, autoRollServiceRequestIds));
            lines.add(summaryLine);
        }
        // 汇总块顺序：药品 → 诊疗 → 耗材（BR-IP-CDT-008）
        lines.sort(Comparator.comparingInt(line -> this.expenseBlockSortOrder(line.getExpenseBlockCode())));
        return lines;
    }

    /**
     * 从候选收费行收集操作人主键（汇总展开明细批量查 Practitioner 用）。
     *
     * <p>操作人由调用方经 {@code ChargeItemActivityOperatorResolver} 解析后传入，
     * 本方法仅汇总 map 的 value（兼容旧调用方先 collect 再查姓名）。</p>
     *
     * @param operatorByChargeItemId chargeItemId → 操作人 practitionerId
     * @return 去重后的操作人 id 集合
     */
    public Set<Long> collectOperatorIdsFromResolvedMap(Map<Long, Long> operatorByChargeItemId) {
        Set<Long> operatorIds = new LinkedHashSet<>();
        if (operatorByChargeItemId == null || operatorByChargeItemId.isEmpty()) {
            return operatorIds;
        }
        for (Long operatorId : operatorByChargeItemId.values()) {
            if (operatorId != null) {
                operatorIds.add(operatorId);
            }
        }
        return operatorIds;
    }

    /**
     * 装配汇总行组内执行/划价/自动计费明细（BR-IP-CDT-018；按业务时间倒序）。
     *
     * @param groupRows                 同 product 分组内的候选行
     * @param product                   展示用产品读模型
     * @param practitionerNameById      操作人 id → 展示名
     * @param operatorByChargeItemId    chargeItemId → 操作人（Procedure / DeviceDispense）
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合
     * @return 展开明细列表
     */
    private List<NurseStationCostSummaryActivityLineDto> buildSummaryActivityLines(
        List<InpatientNurseStationCostDetailChargeRow> groupRows,
        ProductDefinitionDisplayDto product,
        Map<Long, String> practitionerNameById,
        Map<Long, Long> operatorByChargeItemId,
        Set<Long> autoRollServiceRequestIds) {
        List<NurseStationCostSummaryActivityLineDto> activityLines = new ArrayList<>();
        if (groupRows == null || groupRows.isEmpty()) {
            return activityLines;
        }
        List<InpatientNurseStationCostDetailChargeRow> sortedRows = new ArrayList<>(groupRows);
        sortedRows.sort(Comparator.comparing(InpatientNurseStationCostDetailChargeRow::getBusinessDateTime).reversed());
        for (InpatientNurseStationCostDetailChargeRow row : sortedRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            boolean cancelHighlight = this.resolveActivityCancelHighlight(charge);
            boolean anesthesiaPricing =
                GenerateSource.ANESTHESIA_PRICING.getCode().equals(charge.getGenerateSourceEnum());
            boolean anesthesiaExecute =
                GenerateSource.ANESTHESIA_EXECUTE.getCode().equals(charge.getGenerateSourceEnum());
            boolean pricingActivity = this.isPricingGenerateSource(charge.getGenerateSourceEnum());
            boolean autoRollActivity = this.isAutoRollOrderExecuteCharge(charge, autoRollServiceRequestIds);
            String activityTypeEnum;
            String activityTypeDisplay;
            if (pricingActivity) {
                activityTypeEnum = InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_PRICING;
                if (anesthesiaPricing) {
                    if (cancelHighlight) {
                        activityTypeDisplay =
                            InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_CANCEL_ANESTHESIA_PRICING_DISPLAY;
                    } else {
                        activityTypeDisplay =
                            InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_ANESTHESIA_PRICING_DISPLAY;
                    }
                } else if (cancelHighlight) {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_CANCEL_PRICING_DISPLAY;
                } else {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_PRICING_DISPLAY;
                }
            } else if (autoRollActivity) {
                // 滚费落账为 order-execute，但锚点 SR 为 auto-roll-fees → 展示「自动计费」
                activityTypeEnum = InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_AUTO_ROLL;
                if (cancelHighlight) {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_CANCEL_AUTO_ROLL_DISPLAY;
                } else {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_AUTO_ROLL_DISPLAY;
                }
            } else {
                activityTypeEnum = InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_EXECUTE;
                if (anesthesiaExecute) {
                    if (cancelHighlight) {
                        activityTypeDisplay =
                            InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_CANCEL_ANESTHESIA_EXECUTE_DISPLAY;
                    } else {
                        activityTypeDisplay =
                            InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_ANESTHESIA_EXECUTE_DISPLAY;
                    }
                } else if (cancelHighlight) {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_CANCEL_EXECUTE_DISPLAY;
                } else {
                    activityTypeDisplay =
                        InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_EXECUTE_DISPLAY;
                }
            }
            NurseStationCostSummaryActivityLineDto activityLine = new NurseStationCostSummaryActivityLineDto();
            activityLine.setChargeItemId(charge.getId());
            // 组内倒序/列展示：客户可见轴（recorded / 发放 / 滚费 occurrence）
            activityLine.setActivityDateTime(row.getBusinessDateTime());
            activityLine.setDisplayDateTime(row.getDisplayDateTime());
            Long operatorId = this.resolveActivityOperatorId(charge.getId(), operatorByChargeItemId);
            if (operatorId != null && practitionerNameById != null) {
                activityLine.setOperatorName(practitionerNameById.get(operatorId));
            }
            activityLine.setQuantityValue(charge.getQuantityValue());
            activityLine.setQuantityUnit(charge.getQuantityUnit());
            activityLine.setQuantityUnitDisplay(
                nurseStationCostDetailReadAssembler.resolveQuantityUnitDisplay(charge.getQuantityUnit(), product));
            // 展开子表「总价」：账单行合计原样（冲销负额保留，与明细视图同口径）
            activityLine.setTotalPrice(charge.getTotalPrice());
            activityLine.setActivityTypeEnum(activityTypeEnum);
            activityLine.setActivityTypeDisplay(activityTypeDisplay);
            activityLine.setCancelHighlight(cancelHighlight);
            activityLines.add(activityLine);
        }
        return activityLines;
    }

    /**
     * 解析汇总展开明细操作人：只读预解析 map（Procedure 主执行人 / DeviceDispense 发放或配药人）。
     *
     * <p>{@code enterer_*} 语义为收费人/收费时间（BR-OPC-016），禁止再当作划价/执行操作人。</p>
     *
     * @param chargeItemId           收费明细主键
     * @param operatorByChargeItemId 预解析操作人 map
     * @return 操作人 Practitioner 主键；无则 null
     */
    private Long resolveActivityOperatorId(Long chargeItemId, Map<Long, Long> operatorByChargeItemId) {
        if (chargeItemId == null || operatorByChargeItemId == null || operatorByChargeItemId.isEmpty()) {
            return null;
        }
        return operatorByChargeItemId.get(chargeItemId);
    }

    /**
     * 判定是否为站内划价/滚费配置来源码。
     *
     * <p>含护士划价、医技划价；历史 {@code auto-roll-fees} Charge（现行滚费 Charge 为 order-execute）。</p>
     *
     * @param generateSourceEnum 生成来源 code
     * @return true 表示站内划价或历史滚费配置源
     */
    private boolean isPricingGenerateSource(String generateSourceEnum) {
        // 护士划价 / 麻醉划价 / 医技划价；历史 auto-roll-fees Charge
        if (GenerateSource.isNurseStationPricingFamilyCode(generateSourceEnum)) {
            return true;
        }
        return GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(generateSourceEnum)
            || GenerateSource.AUTO_ROLL_FEES.getCode().equals(generateSourceEnum);
    }

    /**
     * 判定是否为床位自动计费落账行：Charge {@code order-execute} 且锚点 SR 为 {@code auto-roll-fees}。
     *
     * @param charge                    收费明细
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合
     * @return true 时活动类型展示为「自动计费」
     */
    private boolean isAutoRollOrderExecuteCharge(ChargeItem charge, Set<Long> autoRollServiceRequestIds) {
        if (charge == null || charge.getServiceId() == null) {
            return false;
        }
        if (!GenerateSource.ORDER_EXECUTE.getCode().equals(charge.getGenerateSourceEnum())) {
            return false;
        }
        if (autoRollServiceRequestIds == null || autoRollServiceRequestIds.isEmpty()) {
            return false;
        }
        return autoRollServiceRequestIds.contains(charge.getServiceId());
    }

    /**
     * 判定汇总展开明细是否为取消执行/冲销（BR-IP-CDT-018）。
     *
     * <p>护士划价负向留痕行活动类型展示为「冲销」；医嘱执行类为「取消执行」。</p>
     *
     * @param charge 收费明细
     * @return true 时前端标红
     */
    private boolean resolveActivityCancelHighlight(ChargeItem charge) {
        if (charge == null) {
            return false;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(charge.getStatusEnum())
            || ChargeItemStatus.REFUNDED.getCode().equals(charge.getStatusEnum())) {
            return true;
        }
        if (charge.getRefundId() != null) {
            return true;
        }
        BigDecimal quantityValue = charge.getQuantityValue();
        if (quantityValue != null && quantityValue.signum() < 0) {
            return true;
        }
        BigDecimal totalPrice = charge.getTotalPrice();
        if (totalPrice != null && totalPrice.signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 装配合计块（IF-IP-CDT-02；total_price 净额，BR-IP-CDT-010）。
     *
     * @param rows             域查询候选行
     * @param productByKey     产品读模型索引
     * @param parentChargeById 父账单索引
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
            if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
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
     * 带子项诊疗展示用产品 id：子项账单取父项 product_id（BR-IP-CDT-006）。
     *
     * @param charge           当前收费行
     * @param parentChargeById 父行索引
     * @return 展示用产品 id
     */
    public Long resolveDisplayProductId(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        return nurseStationCostDetailReadAssembler.resolveDisplayProductId(charge, parentChargeById);
    }

    /**
     * 带子项诊疗展示用产品表名：子项账单取父项 product_table（BR-IP-CDT-006）。
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
