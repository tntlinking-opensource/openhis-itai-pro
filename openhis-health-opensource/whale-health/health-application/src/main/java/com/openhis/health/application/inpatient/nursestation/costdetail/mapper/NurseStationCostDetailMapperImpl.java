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

package com.openhis.health.application.inpatient.nursestation.costdetail.mapper;

import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailPresentationOptionDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.QueryNurseStationInpatientCostDetailRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.LoadNurseStationCostDetailPageInitResponse;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.QueryNurseStationInpatientCostDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.application.inpatient.nursestation.costdetail.assembler.NurseStationCostDetailAssembler;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link NurseStationCostDetailMapper} 实现：字段搬运与初始化常量下发，无跨域编排。
 */
@Component
@RequiredArgsConstructor
public class NurseStationCostDetailMapperImpl implements NurseStationCostDetailMapper {

    /** 组套父项 product 解析（与 Assembler 同构，避免 Mapper/Assembler 重复维护 BR-IP-CDT-006）。 */
    private final NurseStationCostDetailAssembler nurseStationCostDetailAssembler;

    /**
     * 组装费用明细页初始化响应（IF-IP-CDT-01）。
     *
     * @param today 服务端当日
     * @return 默认日期区间与展示选项
     */
    @Override
    public LoadNurseStationCostDetailPageInitResponse buildPageInitResponse(LocalDate today) {
        LoadNurseStationCostDetailPageInitResponse response = new LoadNurseStationCostDetailPageInitResponse();
        // 默认查询区间为服务端当日闭区间（BR-IP-CDT-002）
        response.setDateFrom(today);
        response.setDateTo(today);
        List<NurseStationCostDetailPresentationOptionDto> options = new ArrayList<>();
        NurseStationCostDetailPresentationOptionDto detailOption = new NurseStationCostDetailPresentationOptionDto();
        detailOption.setCode(InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_DETAIL);
        detailOption.setDisplay(InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_DETAIL_DISPLAY);
        options.add(detailOption);
        NurseStationCostDetailPresentationOptionDto summaryOption = new NurseStationCostDetailPresentationOptionDto();
        summaryOption.setCode(InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_SUMMARY);
        summaryOption.setDisplay(InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_SUMMARY_DISPLAY);
        options.add(summaryOption);
        response.setPresentationOptions(options);
        List<String> blockOrder = new ArrayList<>();
        blockOrder.add(InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION);
        blockOrder.add(InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE);
        blockOrder.add(InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE);
        response.setExpenseBlockOrder(blockOrder);
        return response;
    }

    /**
     * 查询请求 → 收费明细域查询命令（IF-IP-CDT-02 Pre）。
     *
     * @param request 查询请求；调用方保证非空
     * @return 域查询命令
     */
    @Override
    public ChargeItemQueryCommand toChargeItemQueryCommand(QueryNurseStationInpatientCostDetailRequest request) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterId(request.getEncounterId());
        command.setBusinessDateFrom(request.getDateFrom());
        command.setBusinessDateTo(request.getDateTo());
        return command;
    }

    /**
     * 护士站可见性查询入参（IF-IP-CDT-02 Gate）。
     *
     * @param encounterIds   目标就诊列表
     * @param managedWardIds 管理病区 id 列表
     * @return 住院信息读模型查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 候选收费行 + 组套父行 → 产品读模型批量加载键（IF-IP-CDT-02 Pre）。
     *
     * @param rows       域查询候选行
     * @param parentById 父 ChargeItem 索引
     * @return 去重后的批量加载键
     */
    @Override
    public List<ProductDefinitionBatchLookup> toProductBatchLookups(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<Long, ChargeItem> parentById) {
        Set<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return lookups;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long productId = nurseStationCostDetailAssembler.resolveDisplayProductId(charge, parentById);
            String productTable = nurseStationCostDetailAssembler.resolveDisplayProductTable(charge, parentById);
            if (productId == null || StringUtils.isBlank(productTable)) {
                continue;
            }
            String dedupeKey = ProductDefinitionKeyUtil.buildCompositeKey(productTable.trim(), productId);
            if (dedupeKey == null) {
                continue;
            }
            if (dedupeKeys.add(dedupeKey)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(productTable.trim());
                lookup.setProductDefinitionId(productId);
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 装配查询响应（IF-IP-CDT-02 Asm）。
     *
     * @param presentationMode 展示模式
     * @param detailLines      明细行；DETAIL 模式非空
     * @param summaryLines     汇总行；SUMMARY 模式非空
     * @param totals           合计块
     * @return 查询响应
     */
    @Override
    public QueryNurseStationInpatientCostDetailResponse toQueryResponse(
        String presentationMode,
        List<NurseStationCostDetailLineDto> detailLines,
        List<NurseStationCostSummaryLineDto> summaryLines,
        NurseStationCostDetailTotalsDto totals) {
        QueryNurseStationInpatientCostDetailResponse response = new QueryNurseStationInpatientCostDetailResponse();
        response.setPresentationMode(presentationMode);
        if (detailLines != null) {
            response.setDetailLines(detailLines);
        } else {
            response.setDetailLines(new ArrayList<>());
        }
        if (summaryLines != null) {
            response.setSummaryLines(summaryLines);
        } else {
            response.setSummaryLines(new ArrayList<>());
        }
        response.setTotals(totals);
        return response;
    }
}
