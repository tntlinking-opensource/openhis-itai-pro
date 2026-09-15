/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.support;

import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailFooterDto;
import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.InsuranceFeeDetailReportConstants;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsuranceSettlementFeeDetailQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailLineExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailPageExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailSummaryExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryModuleExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 医保费用明细报表查询编排（门诊/住院共用：时间窗、互斥、合同/就诊预解析、SPI 调用）。
 *
 * <p>禁止依赖 feature Request/Response；入参为原始筛选字段。</p>
 */
@Component
@RequiredArgsConstructor
public class InsuranceFeeDetailReportQuerySupport {

    /** 合同分类 → 合同 id。 */
    private final ContractQueryService contractQueryService;

    /** 就诊 scope 与 busNo enrich 前置。 */
    private final EncounterQueryService encounterQueryService;

    /** 姓名模糊 → 患者 id → 就诊白名单。 */
    private final PatientQueryService patientQueryService;

    /** 科室下拉。 */
    private final OrganizationQueryService organizationQueryService;

    /** 结算回执 SPI。 */
    private final InsuranceSettlementRecordLookupExtension settlementRecordLookupExtension;

    /** 工伤 Module 门控。 */
    private final WorkInjuryModuleExtension workInjuryModuleExtension;

    /** busNo enrich。 */
    private final InsuranceFeeDetailReportEnricher enricher;

    /**
     * 工伤 Module 是否启用（init 下发 workInjuryEnabled）。
     *
     * @return true 时前端可展示工伤选项
     */
    public boolean isWorkInjuryEnabled() {
        return this.workInjuryModuleExtension.isEnabled();
    }

    /**
     * 门诊挂号科室 OptionDto。
     *
     * @return 科室选项
     */
    public List<OptionDto> buildOutpatientOrganizationOptions() {
        return this.toOrganizationOptions(
            this.organizationQueryService.listOrganizationsForOutpatientRegistration());
    }

    /**
     * 住院入院科室 OptionDto。
     *
     * @return 科室选项
     */
    public List<OptionDto> buildInpatientOrganizationOptions() {
        return this.toOrganizationOptions(
            this.organizationQueryService.listOrganizationsForInpatientAdmission());
    }

    /**
     * 省/市费用类别 OptionDto。
     *
     * @return 类别选项
     */
    public List<OptionDto> buildContractCategoryOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto provincial = new OptionDto();
        provincial.setValue(InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_PROVINCIAL);
        provincial.setLabel("省医保");
        options.add(provincial);
        OptionDto municipal = new OptionDto();
        municipal.setValue(InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_MUNICIPAL);
        municipal.setLabel("市医保");
        options.add(municipal);
        return options;
    }

    /**
     * 职工/居民分组 OptionDto（不含离休）。
     *
     * @return 分组选项
     */
    public List<OptionDto> buildInsutypeGroupOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto all = new OptionDto();
        all.setValue(InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_ALL);
        all.setLabel("全部");
        options.add(all);
        OptionDto employee = new OptionDto();
        employee.setValue(InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_EMPLOYEE);
        employee.setLabel("职工");
        options.add(employee);
        OptionDto resident = new OptionDto();
        resident.setValue(InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_RESIDENT);
        resident.setLabel("居民");
        options.add(resident);
        return options;
    }

    /**
     * 解析并校验结算时间闭区间；倒挂或格式非法抛业务异常。
     *
     * @param dateFrom   起时刻文案
     * @param dateTo     止时刻文案
     * @param exInvalid  非法参数语义码
     * @return [inclusiveStart, inclusiveEnd]
     */
    public LocalDateTime[] parseRequiredDateTimeWindow(String dateFrom, String dateTo, String exInvalid) {
        // Gate：时间窗必填
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            from = ReportDateTimeWindowSupport.parseInclusiveStart(dateFrom);
            to = ReportDateTimeWindowSupport.parseInclusiveEnd(dateTo);
        } catch (DateTimeParseException parseException) {
            throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别", parseException);
        }
        // Gate：倒挂拒绝
        if (to.isBefore(from)) {
            throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
        }
        return new LocalDateTime[] {from, to};
    }

    /**
     * 校验分页窗口为正整数。
     *
     * @param pageNo    页码
     * @param pageSize  页大小
     * @param exInvalid 非法码
     */
    public void assertPageWindow(Integer pageNo, Integer pageSize, String exInvalid) {
        // Gate：页码须 ≥1
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(exInvalid, "请选择有效的分页参数");
        }
        // Gate：页大小须落在 1～MAX_PAGE_SIZE，防止一次拉爆内存
        if (pageSize == null || pageSize < 1 || pageSize > InsuranceFeeDetailReportConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(exInvalid, "请选择有效的分页参数");
        }
    }

    /**
     * 校验费用类别与工伤互斥；非工伤须至少选一类省/市。
     *
     * @param workInjuryOnly         仅工伤
     * @param contractCategoryCodes  省/市码
     * @param exInvalid              非法码
     */
    public void assertCategorySelection(
            Boolean workInjuryOnly,
            List<String> contractCategoryCodes,
            String exInvalid) {
        boolean injury = Boolean.TRUE.equals(workInjuryOnly);
        // 工伤开启但 Module 未装配：拒绝，避免空 settle_mode 扫全表
        if (injury && !this.workInjuryModuleExtension.isEnabled()) {
            throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
        }
        if (injury) {
            // 工伤与省/市互斥：同时勾选省/市则拒绝
            if (contractCategoryCodes != null && !contractCategoryCodes.isEmpty()) {
                throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
            }
            return;
        }
        if (contractCategoryCodes == null || contractCategoryCodes.isEmpty()) {
            throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
        }
        // 类别码须在省/市白名单内
        for (String code : contractCategoryCodes) {
            String normalized = StringUtils.trimToNull(code);
            if (normalized == null) {
                throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
            }
            if (!InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_PROVINCIAL.equals(normalized)
                && !InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_MUNICIPAL.equals(normalized)) {
                throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
            }
        }
    }

    /**
     * 明细分页 + 页脚合计（DB 分页 + SQL SUM）。
     *
     * @param encounterClass         AMB/IMP
     * @param dateFromInclusive      窗起
     * @param dateToInclusive        窗止（闭）
     * @param workInjuryOnly         仅工伤
     * @param contractCategoryCodes  省/市
     * @param insutypeGroup          ALL/EMPLOYEE/RESIDENT
     * @param organizationId         科室
     * @param setlIdKeyword          结算 ID 模糊
     * @param mdtrtIdKeyword         就医登记号模糊
     * @param busNoKeyword           业务号模糊
     * @param patientNameKeyword     姓名模糊
     * @param pageNo                 页码
     * @param pageSize               页大小
     * @param exInvalid              参数非法码
     * @return 展示行 Page + 页脚
     */
    public InsuranceFeeDetailPageBundle pageWithFooter(
            String encounterClass,
            LocalDateTime dateFromInclusive,
            LocalDateTime dateToInclusive,
            Boolean workInjuryOnly,
            List<String> contractCategoryCodes,
            String insutypeGroup,
            Long organizationId,
            String setlIdKeyword,
            String mdtrtIdKeyword,
            String busNoKeyword,
            String patientNameKeyword,
            Integer pageNo,
            Integer pageSize,
            String exInvalid) {
        this.assertCategorySelection(workInjuryOnly, contractCategoryCodes, exInvalid);
        this.assertPageWindow(pageNo, pageSize, exInvalid);
        InsuranceSettlementFeeDetailQueryExtensionIn query = this.buildSpiQuery(
            encounterClass,
            dateFromInclusive,
            dateToInclusive,
            workInjuryOnly,
            contractCategoryCodes,
            insutypeGroup,
            organizationId,
            setlIdKeyword,
            mdtrtIdKeyword,
            busNoKeyword,
            patientNameKeyword,
            pageNo,
            pageSize,
            exInvalid);
        // Cmd：SPI 明细分页
        InsuranceSettlementFeeDetailPageExtensionOut pageOut =
            this.settlementRecordLookupExtension.pageConfirmedFeeDetailLines(query);
        // Cmd：同筛选 SQL 合计
        InsuranceSettlementFeeDetailSummaryExtensionOut summaryOut =
            this.settlementRecordLookupExtension.summarizeConfirmedFeeDetailAmounts(query);
        List<InsuranceFeeDetailRowDto> rows = this.enricher.enrichRows(this.toLineList(pageOut));
        long total = 0L;
        if (pageOut != null) {
            total = pageOut.getTotalCount();
        }
        Page<InsuranceFeeDetailRowDto> page = new PageImpl<>(
            rows,
            PageRequest.of(pageNo - 1, pageSize),
            total);
        return new InsuranceFeeDetailPageBundle(page, this.toFooter(summaryOut));
    }

    /**
     * 导出收集明细行（循环分页至上限）。
     *
     * @param encounterClass         AMB/IMP
     * @param dateFromInclusive      窗起
     * @param dateToInclusive        窗止
     * @param workInjuryOnly         仅工伤
     * @param contractCategoryCodes  省/市
     * @param insutypeGroup          险种分组
     * @param organizationId         科室
     * @param setlIdKeyword          结算 ID
     * @param mdtrtIdKeyword         就医登记号
     * @param busNoKeyword           业务号
     * @param patientNameKeyword     姓名
     * @param exInvalid              非法码
     * @return 导出行与是否截断
     */
    public InsuranceFeeDetailExportBundle collectExportRows(
            String encounterClass,
            LocalDateTime dateFromInclusive,
            LocalDateTime dateToInclusive,
            Boolean workInjuryOnly,
            List<String> contractCategoryCodes,
            String insutypeGroup,
            Long organizationId,
            String setlIdKeyword,
            String mdtrtIdKeyword,
            String busNoKeyword,
            String patientNameKeyword,
            String exInvalid) {
        this.assertCategorySelection(workInjuryOnly, contractCategoryCodes, exInvalid);
        List<InsuranceFeeDetailRowDto> exportRows = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        int pageSize = InsuranceFeeDetailReportConstants.DEFAULT_PAGE_SIZE;
        // 循环拉取直至空页或触顶 EXPORT_MAX_ROWS
        while (exportRows.size() < InsuranceFeeDetailReportConstants.EXPORT_MAX_ROWS) {
            InsuranceSettlementFeeDetailQueryExtensionIn query = this.buildSpiQuery(
                encounterClass,
                dateFromInclusive,
                dateToInclusive,
                workInjuryOnly,
                contractCategoryCodes,
                insutypeGroup,
                organizationId,
                setlIdKeyword,
                mdtrtIdKeyword,
                busNoKeyword,
                patientNameKeyword,
                pageNo,
                pageSize,
                exInvalid);
            InsuranceSettlementFeeDetailPageExtensionOut pageOut =
                this.settlementRecordLookupExtension.pageConfirmedFeeDetailLines(query);
            List<InsuranceSettlementFeeDetailLineExtensionOut> content = this.toLineList(pageOut);
            // 空页：结束
            if (content.isEmpty()) {
                break;
            }
            List<InsuranceFeeDetailRowDto> pageRows = this.enricher.enrichRows(content);
            for (InsuranceFeeDetailRowDto row : pageRows) {
                if (exportRows.size() >= InsuranceFeeDetailReportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                exportRows.add(row);
            }
            if (truncated) {
                break;
            }
            if (content.size() < pageSize) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return new InsuranceFeeDetailExportBundle(exportRows, truncated);
    }

    /**
     * 组装 SPI 查询入参（含合同 id / 就诊白名单 / 险种）。
     *
     * @param encounterClass         AMB/IMP
     * @param dateFromInclusive      窗起
     * @param dateToInclusive        窗止闭区间
     * @param workInjuryOnly         仅工伤
     * @param contractCategoryCodes  省/市
     * @param insutypeGroup          险种分组
     * @param organizationId         科室
     * @param setlIdKeyword          结算 ID
     * @param mdtrtIdKeyword         就医登记号
     * @param busNoKeyword           业务号
     * @param patientNameKeyword     姓名
     * @param pageNo                 页码
     * @param pageSize               页大小
     * @param exInvalid              非法码
     * @return SPI ExtensionIn
     */
    private InsuranceSettlementFeeDetailQueryExtensionIn buildSpiQuery(
            String encounterClass,
            LocalDateTime dateFromInclusive,
            LocalDateTime dateToInclusive,
            Boolean workInjuryOnly,
            List<String> contractCategoryCodes,
            String insutypeGroup,
            Long organizationId,
            String setlIdKeyword,
            String mdtrtIdKeyword,
            String busNoKeyword,
            String patientNameKeyword,
            Integer pageNo,
            Integer pageSize,
            String exInvalid) {
        InsuranceSettlementFeeDetailQueryExtensionIn query =
            new InsuranceSettlementFeeDetailQueryExtensionIn();
        query.setEncounterClass(encounterClass);
        query.setSetlTimeFromInclusive(dateFromInclusive);
        query.setSetlTimeToExclusive(ReportDateTimeWindowSupport.toExclusiveEnd(dateToInclusive));
        query.setWorkInjuryOnly(Boolean.TRUE.equals(workInjuryOnly));
        // 非工伤：解析省/市合同 id 并集
        if (!Boolean.TRUE.equals(workInjuryOnly)) {
            query.setContractIds(this.resolveContractIds(contractCategoryCodes, exInvalid));
        }
        query.setInsutypes(this.resolveInsutypes(insutypeGroup, exInvalid));
        EncounterScopeResult scope =
            this.resolveEncounterScope(encounterClass, organizationId, busNoKeyword, patientNameKeyword);
        query.setEmptyEncounterScope(scope.emptyScope);
        query.setEncounterIds(scope.encounterIds);
        query.setSetlIdKeyword(StringUtils.trimToNull(setlIdKeyword));
        query.setMdtrtIdKeyword(StringUtils.trimToNull(mdtrtIdKeyword));
        // 姓名已并入就诊白名单；不再下推 SPI psn_name（院端回执常为「患者」占位）
        query.setPatientNameKeyword(null);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 省/市类别码 → 合同主键并集；无合同则空列表（SPI 空集）。
     *
     * <p>主数据 {@code category_enum} 两套码并存：UI/契约用 {@code PROVINCIAL_INSURANCE}/
     * {@code MUNICIPAL_INSURANCE}，库内常见 {@link ChargeTypeCategory} 数字码 {@code 3}/{@code 4}；
     * 须并查，否则合同 id 全空 → 报表恒无行（与日结费用性质识别同口径）。</p>
     *
     * @param contractCategoryCodes 类别码
     * @param exInvalid             非法码
     * @return 合同 id
     */
    private List<Long> resolveContractIds(List<String> contractCategoryCodes, String exInvalid) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (contractCategoryCodes == null) {
            return new ArrayList<>();
        }
        for (String code : contractCategoryCodes) {
            String normalized = StringUtils.trimToNull(code);
            if (normalized == null) {
                throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
            }
            // 并查枚举名码 + 财务维护数字码
            for (String persistenceCode : this.expandCategoryPersistenceCodes(normalized)) {
                List<Long> byCategory = this.contractQueryService.listIdsByCategoryEnum(persistenceCode);
                if (byCategory == null) {
                    continue;
                }
                for (Long id : byCategory) {
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * UI 省/市类别码 → 库内可能出现的 {@code category_enum} 字面量列表。
     *
     * @param uiCategoryCode {@code PROVINCIAL_INSURANCE} / {@code MUNICIPAL_INSURANCE}
     * @return  persistence 码（含入参自身）
     */
    private List<String> expandCategoryPersistenceCodes(String uiCategoryCode) {
        List<String> codes = new ArrayList<>();
        codes.add(uiCategoryCode);
        if (InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_PROVINCIAL.equals(uiCategoryCode)) {
            codes.add(ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode());
            return codes;
        }
        if (InsuranceFeeDetailReportConstants.CONTRACT_CATEGORY_MUNICIPAL.equals(uiCategoryCode)) {
            codes.add(ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode());
            return codes;
        }
        return codes;
    }

    /**
     * 职工/居民分组 → 平台 insutype 列表；ALL/空 → null（不收窄）。
     *
     * @param insutypeGroup 分组码
     * @param exInvalid     非法码
     * @return insutype 列表或 null
     */
    private List<String> resolveInsutypes(String insutypeGroup, String exInvalid) {
        String group = StringUtils.trimToNull(insutypeGroup);
        if (group == null || InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_ALL.equals(group)) {
            return null;
        }
        if (InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_EMPLOYEE.equals(group)) {
            List<String> employee = new ArrayList<>();
            employee.add(InsuranceFeeDetailReportConstants.INSUTYPE_EMPLOYEE_BASIC);
            employee.add(InsuranceFeeDetailReportConstants.INSUTYPE_EMPLOYEE_CIVIL);
            employee.add(InsuranceFeeDetailReportConstants.INSUTYPE_EMPLOYEE_LARGE);
            return employee;
        }
        if (InsuranceFeeDetailReportConstants.INSUTYPE_GROUP_RESIDENT.equals(group)) {
            List<String> resident = new ArrayList<>();
            resident.add(InsuranceFeeDetailReportConstants.INSUTYPE_RESIDENT);
            return resident;
        }
        throw new BusinessException(exInvalid, "请选择有效结算时间与费用类别");
    }

    /**
     * 科室 / 业务号 / 姓名 → 就诊白名单；任一条件命中为空则 emptyScope。
     *
     * <p>姓名经患者主数据展开后反查就诊（与列表姓名列同源）；结算回执 {@code psn_name} 不作筛选真源。</p>
     *
     * @param encounterClass     AMB/IMP
     * @param organizationId     科室
     * @param busNoKeyword       业务号模糊
     * @param patientNameKeyword 姓名模糊
     * @return scope 结果
     */
    private EncounterScopeResult resolveEncounterScope(
            String encounterClass,
            Long organizationId,
            String busNoKeyword,
            String patientNameKeyword) {
        boolean hasOrg = organizationId != null;
        String busNo = StringUtils.trimToNull(busNoKeyword);
        boolean hasBusNo = busNo != null;
        String patientName = StringUtils.trimToNull(patientNameKeyword);
        boolean hasPatientName = patientName != null;
        // 无科室/业务号/姓名：不按就诊收窄
        if (!hasOrg && !hasBusNo && !hasPatientName) {
            return EncounterScopeResult.unrestricted();
        }
        Set<Long> current = null;
        if (hasOrg) {
            List<Long> byOrg;
            // 门诊按挂号科室；住院按入院科室
            if (EncounterClass.AMB.getCode().equals(encounterClass)) {
                byOrg = this.encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(organizationId);
            } else {
                byOrg = this.encounterQueryService.listInpatientImpEncounterIdsByOrganization(organizationId);
            }
            current = this.toIdSet(byOrg);
            // 科室下无就诊：直接空
            if (current.isEmpty()) {
                return EncounterScopeResult.empty();
            }
        }
        if (hasBusNo) {
            Set<Long> byBusNo = this.toIdSet(this.encounterQueryService.listEncounterIdsByBusNoKeyword(busNo));
            // 业务号无命中：直接空
            if (byBusNo.isEmpty()) {
                return EncounterScopeResult.empty();
            }
            current = this.intersect(current, byBusNo);
            // 与科室交集为空：直接空
            if (current.isEmpty()) {
                return EncounterScopeResult.empty();
            }
        }
        if (hasPatientName) {
            List<Long> patientIds = this.patientQueryService.listPatientIdsByNameKeyword(
                patientName,
                InsuranceFeeDetailReportConstants.PATIENT_NAME_MATCH_LIMIT);
            // 姓名无命中患者：直接空
            if (patientIds == null || patientIds.isEmpty()) {
                return EncounterScopeResult.empty();
            }
            Set<Long> byPatient =
                this.toIdSet(this.encounterQueryService.listEncounterIdsByPatientIds(patientIds));
            // 患者无关联就诊：直接空
            if (byPatient.isEmpty()) {
                return EncounterScopeResult.empty();
            }
            current = this.intersect(current, byPatient);
            // 与科室/业务号交集为空：直接空
            if (current.isEmpty()) {
                return EncounterScopeResult.empty();
            }
        }
        return EncounterScopeResult.of(new ArrayList<>(current));
    }

    /**
     * Organization 列表 → OptionDto。
     *
     * @param organizations 科室实体
     * @return 选项
     */
    private List<OptionDto> toOrganizationOptions(List<Organization> organizations) {
        List<OptionDto> options = new ArrayList<>();
        if (organizations == null) {
            return options;
        }
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            if (organization.getName() != null) {
                option.setLabel(organization.getName().trim());
            }
            options.add(option);
        }
        return options;
    }

    /**
     * SPI 分页 content 安全取列表。
     *
     * @param pageOut SPI 分页
     * @return 行列表（非 null）
     */
    private List<InsuranceSettlementFeeDetailLineExtensionOut> toLineList(
            InsuranceSettlementFeeDetailPageExtensionOut pageOut) {
        if (pageOut == null || pageOut.getContent() == null) {
            return new ArrayList<>();
        }
        return pageOut.getContent();
    }

    /**
     * SPI 合计 → 页脚（结算 2 位刻度）。
     *
     * @param summaryOut SPI 合计
     * @return 页脚
     */
    private InsuranceFeeDetailFooterDto toFooter(InsuranceSettlementFeeDetailSummaryExtensionOut summaryOut) {
        InsuranceFeeDetailFooterDto footer = new InsuranceFeeDetailFooterDto();
        if (summaryOut == null) {
            footer.setLineCount(0L);
            footer.setMedfeeSumamtTotal(BigDecimal.ZERO);
            footer.setFundPaySumamtTotal(BigDecimal.ZERO);
            footer.setAcctPayTotal(BigDecimal.ZERO);
            footer.setPsnCashPayTotal(BigDecimal.ZERO);
            return footer;
        }
        footer.setLineCount(summaryOut.getLineCount());
        footer.setMedfeeSumamtTotal(MonetaryAmountSupport.scaleSettle(summaryOut.getMedfeeSumamtTotal()));
        footer.setFundPaySumamtTotal(MonetaryAmountSupport.scaleSettle(summaryOut.getFundPaySumamtTotal()));
        footer.setAcctPayTotal(MonetaryAmountSupport.scaleSettle(summaryOut.getAcctPayTotal()));
        footer.setPsnCashPayTotal(MonetaryAmountSupport.scaleSettle(summaryOut.getPsnCashPayTotal()));
        return footer;
    }

    /**
     * id 列表 → LinkedHashSet（去重保序）。
     *
     * @param ids 主键列表
     * @return set
     */
    private Set<Long> toIdSet(List<Long> ids) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (ids == null) {
            return set;
        }
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return set;
    }

    /**
     * 集合相交；left 为 null 时返回 right 副本。
     *
     * @param left  已有集合；null 表示尚未收窄
     * @param right 新条件集合
     * @return 交集
     */
    private Set<Long> intersect(Set<Long> left, Set<Long> right) {
        if (left == null) {
            return new LinkedHashSet<>(right);
        }
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        for (Long id : left) {
            if (right.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * 就诊 scope 预解析结果。
     */
    private static final class EncounterScopeResult {

        /** 为 true 表示条件有但无命中。 */
        private final boolean emptyScope;

        /** 白名单；emptyScope=false 且 null/空表示不收窄。 */
        private final List<Long> encounterIds;

        private EncounterScopeResult(boolean emptyScope, List<Long> encounterIds) {
            this.emptyScope = emptyScope;
            this.encounterIds = encounterIds;
        }

        /**
         * 不按就诊收窄。
         *
         * @return 无限制 scope
         */
        private static EncounterScopeResult unrestricted() {
            return new EncounterScopeResult(false, null);
        }

        /**
         * 预解析无命中。
         *
         * @return 空 scope
         */
        private static EncounterScopeResult empty() {
            return new EncounterScopeResult(true, new ArrayList<>());
        }

        /**
         * 有白名单。
         *
         * @param ids 就诊 id
         * @return scope
         */
        private static EncounterScopeResult of(List<Long> ids) {
            return new EncounterScopeResult(false, ids);
        }
    }

    /**
     * 分页 + 页脚捆绑。
     *
     * @param page   展示页
     * @param footer 页脚
     */
    public record InsuranceFeeDetailPageBundle(
        Page<InsuranceFeeDetailRowDto> page,
        InsuranceFeeDetailFooterDto footer) {
    }

    /**
     * 导出行捆绑。
     *
     * @param rows      行
     * @param truncated 是否触顶截断
     */
    public record InsuranceFeeDetailExportBundle(
        List<InsuranceFeeDetailRowDto> rows,
        boolean truncated) {
    }
}
