/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.mapper;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailLineDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.QueryNurseStationInpatientCostDetailRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.LoadNurseStationCostDetailPageInitResponse;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.QueryNurseStationInpatientCostDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站费用明细 Mapper：Request/Command/DTO 字段搬运，不承载业务流程判断。
 */
public interface NurseStationCostDetailMapper {

    /**
     * 组装费用明细页初始化响应（IF-IP-CDT-01）。
     *
     * @param today 服务端当日
     * @return 默认日期区间与展示选项
     */
    LoadNurseStationCostDetailPageInitResponse buildPageInitResponse(LocalDate today);

    /**
     * 查询请求 → 收费明细域查询命令（IF-IP-CDT-02 Pre）。
     *
     * @param request 查询请求；调用方保证非空
     * @return 域查询命令
     */
    ChargeItemQueryCommand toChargeItemQueryCommand(QueryNurseStationInpatientCostDetailRequest request);

    /**
     * 护士站可见性查询入参（IF-IP-CDT-02 Gate）。
     *
     * @param encounterIds   目标就诊列表
     * @param managedWardIds 管理病区 id 列表
     * @return 住院信息读模型查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * 候选收费行 + 组套父行 → 产品读模型批量加载键（IF-IP-CDT-02 Pre）。
     *
     * @param rows          域查询候选行
     * @param parentById    父 ChargeItem 索引
     * @return 去重后的批量加载键
     */
    List<ProductDefinitionBatchLookup> toProductBatchLookups(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<Long, ChargeItem> parentById);

    /**
     * 装配查询响应（IF-IP-CDT-02 Asm）。
     *
     * @param presentationMode 展示模式
     * @param detailLines      明细行；DETAIL 模式非空
     * @param summaryLines     汇总行；SUMMARY 模式非空
     * @param totals           合计块
     * @return 查询响应
     */
    QueryNurseStationInpatientCostDetailResponse toQueryResponse(
        String presentationMode,
        List<NurseStationCostDetailLineDto> detailLines,
        List<NurseStationCostSummaryLineDto> summaryLines,
        NurseStationCostDetailTotalsDto totals);
}
