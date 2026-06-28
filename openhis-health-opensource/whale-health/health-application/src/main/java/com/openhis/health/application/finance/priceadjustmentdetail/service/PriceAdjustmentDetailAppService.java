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

package com.openhis.health.application.finance.priceadjustmentdetail.service;

import com.openhis.health.application.finance.priceadjustment.dto.PriceAdjustmentLineDto;
import com.openhis.health.application.finance.priceadjustment.mapper.PriceAdjustmentMapper;
import com.openhis.health.application.finance.priceadjustmentdetail.dto.PriceAdjustmentApprovedLineListRowDto;
import com.openhis.health.application.finance.priceadjustmentdetail.mapper.PriceAdjustmentDetailMapper;
import com.openhis.health.application.finance.priceadjustmentdetail.request.GetPriceAdjustmentApprovedLineRequest;
import com.openhis.health.application.finance.priceadjustmentdetail.request.InitPriceAdjustmentDetailRequest;
import com.openhis.health.application.finance.priceadjustmentdetail.request.PagePriceAdjustmentApprovedLineRequest;
import com.openhis.health.application.finance.priceadjustmentdetail.response.GetPriceAdjustmentApprovedLineResponse;
import com.openhis.health.application.finance.priceadjustmentdetail.response.InitPriceAdjustmentDetailResponse;
import com.openhis.health.application.finance.priceadjustmentdetail.response.PagePriceAdjustmentApprovedLineResponse;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PriceAdjustmentDetailConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.querysupport.priceadjustment.PriceAdjustmentDetailItemKeywordSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 财务管理 · 调价明细应用编排（AS-FIN-PRC-002，IF-PRC-DTL-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>只读查询已生效调价项目主行与子表批号明细；写侧制单/审批见 PRC-001。</li>
 *   <li>读侧委托 {@link PriceAdjustmentQueryService}；关键字预解析委托
 *       {@link PriceAdjustmentDetailItemKeywordSupport}。</li>
 *   <li>目录 enrich 委托 {@link ProductDefinitionBatchLoader}；详情行 DTO 复用 PRC-001 {@link PriceAdjustmentMapper}。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 注册为
 * {@code POST /api/app/priceAdjustmentDetail/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAdjustmentDetailAppService {

    /** 读路径登录 Gate（不做数据权限收窄）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 已生效调价主行读侧。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /** 项目关键字 → item_id 预解析。 */
    private final PriceAdjustmentDetailItemKeywordSupport itemKeywordSupport;

    /** 产品定义批量 enrich。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 制单/审批人展示名 enrich。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 调价明细 Request/Response 映射。 */
    private final PriceAdjustmentDetailMapper priceAdjustmentDetailMapper;

    /** PRC-001 行 DTO 映射（详情 IF 只读复用）。 */
    private final PriceAdjustmentMapper priceAdjustmentMapper;

    /**
     * 调价明细页初始化（IF-PRC-DTL-01）。
     *
     * @param request 空体占位
     * @return 默认生效时间窗、Tab 选项与默认分页大小
     * @throws BusinessException 未登录时
     */
    public InitPriceAdjustmentDetailResponse initPriceAdjustmentDetail(InitPriceAdjustmentDetailRequest request) {
        // Gate：须已登录方可进入租户内只读页
        this.currentOperatorResolver.requireOperatorId(
            PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
            "调价明细查询失败，请稍后重试");
        try {
            // Asm：投影默认当日时间窗、item_table Tab 与调价原因枚举
            return this.priceAdjustmentDetailMapper.toInitPriceAdjustmentDetailResponse();
        } catch (RuntimeException runtimeException) {
            // Gate：初始化异常映射 EX-PRC-DTL-104，避免裸异常透出
            log.error("调价明细页初始化失败", runtimeException);
            throw new BusinessException(
                PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
                "调价明细查询失败，请稍后重试");
        }
    }

    /**
     * 分页查询已生效调价项目主行（IF-PRC-DTL-02）。
     *
     * @param request 筛选与分页参数
     * @return 主行列表（含盈亏合计；不含调前/调后价与子表）
     * @throws BusinessException 时间非法、itemTable 非法、目录 enrich 失败或查询异常时
     */
    public PagePriceAdjustmentApprovedLineResponse pagePriceAdjustmentApprovedLine(
        PagePriceAdjustmentApprovedLineRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
            "调价明细查询失败，请稍后重试");
        // Gate：请求体非空
        if (request == null) {
            // 空请求无法解析生效时间与分页，直接拒绝
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "请求不能为空");
        }
        // Gate：生效时间闭区间合法（BR-PRC-DTL-001）
        this.validateApproveTimeWindow(request.getApproveTimeFrom(), request.getApproveTimeTo());
        String itemTable = trimToNull(request.getItemTable());
        // Gate：item_table 非空时须为四档合法值（BR-PRC-DTL-004）
        if (itemTable != null && !PriceAdjustmentItemTableSupport.isSupportedItemTable(itemTable)) {
            // 非法 Tab 无法下推 item_table 谓词，防止误查全表
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "参数错误");
        }
        try {
            List<Long> itemIds = null;
            String nameKeyword = trimToNull(request.getItemNameKeyword());
            String codeKeyword = trimToNull(request.getItemCodeKeyword());
            if (nameKeyword != null || codeKeyword != null) {
                // Pre：项目名/编码关键字预解析为 item_id 集合（BR-PRC-DTL-005）
                itemIds = this.itemKeywordSupport.resolveItemIds(itemTable, nameKeyword, codeKeyword);
                if (itemIds != null && itemIds.isEmpty()) {
                    // 关键字无命中时直接空页，不访问调价主表（BR-PRC-DTL-011）
                    int pageNo = this.resolvePageNo(request);
                    int pageSize = this.resolvePageSize(request);
                    return this.priceAdjustmentDetailMapper.toPagePriceAdjustmentApprovedLineResponse(
                        Collections.emptyList(),
                        pageNo,
                        pageSize,
                        0L);
                }
            }
            // Pre：Request → 领域分页 Query（approve_time 下推）
            PriceAdjustmentApprovedLineListQuery listQuery =
                this.priceAdjustmentDetailMapper.toPriceAdjustmentApprovedLineListQuery(request, itemIds);
            // Pre：主行计数与分页（硬过滤 approved）
            long totalCount = this.priceAdjustmentQueryService.countApprovedLines(listQuery);
            List<PriceAdjustmentLineQueryModel> lines =
                this.priceAdjustmentQueryService.pageApprovedLines(listQuery);
            // Pre：批量 enrich 项目名/编码
            Map<String, ProductDefinitionDisplayDto> productByKey = this.loadProductDisplayMap(lines);
            this.assertLinesProductEnriched(lines, productByKey);
            // Pre：批量 enrich 制单/审批人
            Map<Long, Practitioner> practitioners = this.loadPractitionersByIds(this.collectPractitionerIds(lines));
            List<PriceAdjustmentApprovedLineListRowDto> rows = new ArrayList<>();
            // Asm：逐行映射列表 DTO，totalImpact 取自读模型固化值
            for (PriceAdjustmentLineQueryModel line : lines) {
                if (line == null) {
                    // 跳过空行，避免 NPE 污染当前页
                    continue;
                }
                ProductDefinitionDisplayDto product = this.resolveProduct(line, productByKey);
                // Asm：禁止填充调前/调后价（BR-PRC-DTL-008）
                PriceAdjustmentApprovedLineListRowDto rowDto =
                    this.priceAdjustmentDetailMapper.toPriceAdjustmentApprovedLineListRowDto(
                        line,
                        product,
                        practitioners);
                if (rowDto != null) {
                    rows.add(rowDto);
                }
            }
            int pageNo = listQuery.getPageNo();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：装配分页响应
            return this.priceAdjustmentDetailMapper.toPagePriceAdjustmentApprovedLineResponse(
                rows,
                pageNo,
                pageSize,
                totalCount);
        } catch (BusinessException businessException) {
            // 业务异常原样抛出，保留 EX-PRC-DTL-* 与领域错误码
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：未预期异常统一映射 EX-PRC-DTL-104
            log.error("调价明细分页查询失败", runtimeException);
            throw new BusinessException(
                PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
                "调价明细查询失败，请稍后重试");
        }
    }

    /**
     * 查看调价项目详情与子表批号明细（IF-PRC-DTL-03）。
     *
     * @param request 主行主键
     * @return 主行调后价、子表调前价与分项盈亏
     * @throws BusinessException 主键非法、记录不存在/未生效或 enrich 失败时
     */
    public GetPriceAdjustmentApprovedLineResponse getPriceAdjustmentApprovedLine(
        GetPriceAdjustmentApprovedLineRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
            "调价明细查询失败，请稍后重试");
        // Gate：主行主键非空
        if (request == null || request.getId() == null) {
            // 无主键无法定位 approved 主行
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "参数错误");
        }
        try {
            PriceAdjustmentLineQueryModel lineModel;
            try {
                // Pre：加载 approved 主行及全部子表（BR-PRC-DTL-010）
                lineModel = this.priceAdjustmentQueryService.getApprovedLineById(request.getId());
            } catch (BusinessException businessException) {
                String errorCode = businessException.getErrorCode();
                if (ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND.equals(errorCode)
                    || ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT.equals(errorCode)) {
                    // 非 approved 或不存在统一映射 EX-PRC-DTL-101（BR-PRC-DTL-010）
                    throw new BusinessException(
                        PriceAdjustmentDetailConstants.EX_PRC_DTL_101,
                        "调价记录不存在或尚未生效");
                }
                throw businessException;
            }
            // Pre：目录 enrich
            Map<String, ProductDefinitionDisplayDto> productByKey =
                this.loadProductDisplayMap(List.of(lineModel));
            this.assertLinesProductEnriched(List.of(lineModel), productByKey);
            // Pre：制单/审批人 enrich
            Map<Long, Practitioner> practitioners =
                this.loadPractitionersByIds(this.collectPractitionerIds(List.of(lineModel)));
            // Asm：复用 PRC-001 行 DTO 映射（含子表调前价与分项盈亏）
            PriceAdjustmentLineDto lineDto = this.priceAdjustmentMapper.toLineDto(lineModel, productByKey);
            String approverName = this.resolvePractitionerName(practitioners, lineModel.getApproverId());
            // Asm：装配详情响应（含行级 totalImpact 与 approveTime）
            return this.priceAdjustmentDetailMapper.toGetPriceAdjustmentApprovedLineResponse(
                lineDto,
                lineModel.getTotalImpact(),
                lineModel.getApproveTime(),
                lineModel.getApproverId(),
                approverName);
        } catch (BusinessException businessException) {
            // 业务异常原样抛出（含 EX-PRC-DTL-101/103）
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：详情加载异常映射 EX-PRC-DTL-104
            log.error("调价明细详情加载失败, id={}", request.getId(), runtimeException);
            throw new BusinessException(
                PriceAdjustmentDetailConstants.EX_PRC_DTL_104,
                "调价明细查询失败，请稍后重试");
        }
    }

    /**
     * Gate：校验生效时间闭区间（**BR-PRC-DTL-001**）。
     *
     * @param approveTimeFrom 下限
     * @param approveTimeTo   上限
     * @throws BusinessException 任一端为空或 from &gt; to 时
     */
    private void validateApproveTimeWindow(LocalDateTime approveTimeFrom, LocalDateTime approveTimeTo) {
        if (approveTimeFrom == null || approveTimeTo == null) {
            // 生效时间必填（BR-PRC-DTL-001）
            throw new BusinessException(
                PriceAdjustmentDetailConstants.EX_PRC_DTL_102,
                "请选择有效的生效日期");
        }
        if (approveTimeFrom.isAfter(approveTimeTo)) {
            // 闭区间要求 from ≤ to
            throw new BusinessException(
                PriceAdjustmentDetailConstants.EX_PRC_DTL_102,
                "请选择有效的生效日期");
        }
    }

    /**
     * 解析请求页码（默认 1）。
     *
     * @param request 分页请求
     * @return 1-based 页码
     */
    private int resolvePageNo(PagePriceAdjustmentApprovedLineRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            // 未传页码时回落第 1 页
            return 1;
        }
        return request.getPageNo();
    }

    /**
     * 解析请求页大小（默认 {@link PriceAdjustmentDetailConstants#DEFAULT_PAGE_SIZE}）。
     *
     * @param request 分页请求
     * @return 有效页大小
     */
    private int resolvePageSize(PagePriceAdjustmentApprovedLineRequest request) {
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            // 未传页大小时使用模块默认 20
            return PriceAdjustmentDetailConstants.DEFAULT_PAGE_SIZE;
        }
        if (request.getPageSize() > 1000) {
            // 与 AbstractPageQuery#getBoundedPageSize 上限对齐
            return 1000;
        }
        return request.getPageSize();
    }

    /**
     * 批量加载主行关联的产品展示映射。
     *
     * @param lines 主行读模型列表
     * @return 复合键 → 展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayMap(List<PriceAdjustmentLineQueryModel> lines) {
        Map<String, ProductDefinitionDisplayDto> empty = new HashMap<>();
        if (lines == null || lines.isEmpty()) {
            // 无行时跳过批量目录加载
            return empty;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        // Pre：收集当前页去重后的 itemTable+itemId 批量 lookup
        for (PriceAdjustmentLineQueryModel line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                // 缺锚点行在 assertLinesProductEnriched 阶段失败
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            if (dedupe.contains(key)) {
                // 同页重复项目只加载一次
                continue;
            }
            dedupe.add(key);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(line.getItemTable());
            lookup.setProductDefinitionId(line.getItemId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            // 无有效 lookup 时不调用 BatchLoader
            return empty;
        }
        return this.productDefinitionBatchLoader.loadAsMap(lookups);
    }

    /**
     * 目录 enrich 完整性校验（**BR-PRC-DTL-012**）：任一行无法解析展示名则整单失败。
     *
     * @param lines         主行读模型
     * @param productByKey  批量加载结果
     * @throws BusinessException enrich 失败时
     */
    private void assertLinesProductEnriched(
        List<PriceAdjustmentLineQueryModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        for (PriceAdjustmentLineQueryModel line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                // 主行缺项目锚点视为目录不完整（BR-PRC-DTL-012）
                throw new BusinessException(
                    PriceAdjustmentDetailConstants.EX_PRC_DTL_103,
                    "调价项目数据不完整，请联系管理员核对目录配置");
            }
            ProductDefinitionDisplayDto product = this.resolveProduct(line, productByKey);
            if (product == null || StringUtils.isBlank(product.getProductName())) {
                // enrich 失败整单拒绝，禁止静默空名展示
                throw new BusinessException(
                    PriceAdjustmentDetailConstants.EX_PRC_DTL_103,
                    "调价项目数据不完整，请联系管理员核对目录配置");
            }
        }
    }

    /**
     * 从批量映射中取单行产品展示 DTO。
     *
     * @param line         主行读模型
     * @param productByKey 复合键 → 展示 DTO
     * @return 展示 DTO；未命中时 null
     */
    private ProductDefinitionDisplayDto resolveProduct(
        PriceAdjustmentLineQueryModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || productByKey == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        return productByKey.get(key);
    }

    /**
     * 收集主行涉及的制单/审批人主键。
     *
     * @param lines 主行读模型列表
     * @return 去重后的主键集合
     */
    private Set<Long> collectPractitionerIds(List<PriceAdjustmentLineQueryModel> lines) {
        Set<Long> ids = new HashSet<>();
        for (PriceAdjustmentLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOperatorId() != null) {
                ids.add(line.getOperatorId());
            }
            if (line.getApproverId() != null) {
                ids.add(line.getApproverId());
            }
        }
        return ids;
    }

    /**
     * 批量加载医务人员（经 Practitioner QS，B.6.4）。
     *
     * @param practitionerIds 主键集合
     * @return 主键 → 实体映射
     */
    private Map<Long, Practitioner> loadPractitionersByIds(Set<Long> practitionerIds) {
        Map<Long, Practitioner> result = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            // 无人员 id 时跳过 Practitioner QS
            return result;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        // Pre：批量结果转为 id → 实体映射供 Mapper enrich
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null) {
                result.put(practitioner.getId(), practitioner);
            }
        }
        return result;
    }

    /**
     * 解析医务人员展示名。
     *
     * @param practitioners   批量映射
     * @param practitionerId  主键
     * @return 展示名；未命中时 null
     */
    private String resolvePractitionerName(Map<Long, Practitioner> practitioners, Long practitionerId) {
        if (practitioners == null || practitionerId == null) {
            return null;
        }
        Practitioner practitioner = practitioners.get(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getName();
    }
}
