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

package com.openhis.health.application.querysupport.product;

import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.common.assembler.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 产品定义展示：术语码表预热与批量回填（B.6.1 / B.10.8.1）。
 */
@Component
@RequiredArgsConstructor
public class ProductDefinitionSelectionTerminologySupport {

    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 加载产品展示所需的术语映射（每请求/每页调用一次即可）。
     *
     * @return 预热后的术语映射容器
     */
    public ProductDefinitionSelectionTerminologyMaps loadSelectionTerminologyMaps() {
        ProductDefinitionSelectionTerminologyMaps maps = new ProductDefinitionSelectionTerminologyMaps();
        maps.setUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE));
        maps.setDoseUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT));
        maps.setMedicationCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        maps.setActivityCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE));
        maps.setDeviceCategoryLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        maps.setMethodLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE));
        maps.setRateLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RATE_CODE));
        maps.setDoseFormLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE));
        maps.setRestrictedEnumLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RESTRICTED_ENUM));
        maps.setPartAttributeLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.PART_ATTRIBUTE_ENUM));
        maps.setThoPartAttributeLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.THO_PART_ATTRIBUTE_ENUM));
        maps.setChrgitmLvLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.CHRGITM_LV));
        return maps;
    }

    /**
     * 批量回填 {@link ProductDefinitionDisplayDto} 各字典展示列。
     *
     * @param rows 展示行集合
     */
    public void enrichDisplayDtos(Collection<ProductDefinitionDisplayDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        ProductDefinitionSelectionTerminologyMaps maps = this.loadSelectionTerminologyMaps();
        ProductDefinitionDisplayTerminologyAssembler.enrichAll(rows, maps);
    }

    /**
     * 库房/进货选品：对已组装的单位槽按 {@code UNIT_CODE} 术语回填 {@code unitName}（B.10.8.1）。
     *
     * @param bands       进货四槽列表
     * @param productRow  产品展示 DTO
     * @param maps        预热术语映射；为 null 时跳过
     */
    public void applyInboundUnitBandDisplayNames(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        ProductDefinitionSelectionTerminologyMaps maps
    ) {
        ProductDefinitionCatalogUnitBandsAssembler.applyInboundUnitBandDisplayNames(bands, productRow, maps);
    }
}
