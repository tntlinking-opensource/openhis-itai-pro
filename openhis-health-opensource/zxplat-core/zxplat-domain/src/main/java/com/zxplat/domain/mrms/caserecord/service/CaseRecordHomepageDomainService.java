/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.zxplat.domain.mrms.caserecord.entity.HomepageBld;
import com.zxplat.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.zxplat.domain.mrms.caserecord.entity.HomepageIcu;
import com.zxplat.domain.mrms.caserecord.entity.HomepageProcedure;
import com.zxplat.domain.share.enums.FieldFillSource;
import com.zxplat.domain.share.enums.HomepageDiagType;
import com.zxplat.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.zxplat.domain.mrms.caserecord.support.CaseRecordAutoFillSupport;
import com.zxplat.domain.mrms.caserecord.support.CaseRecordIdempotentPersistSupport;
import com.zxplat.domain.mrms.caserecord.vo.DiagnosisLineSnapshot;
import com.zxplat.domain.mrms.caserecord.vo.EncounterSnapshot;
import com.zxplat.domain.mrms.caserecord.vo.FeeSummarySnapshot;
import com.zxplat.domain.mrms.caserecord.vo.NursingCareDaysSnapshot;
import com.zxplat.domain.mrms.caserecord.vo.PatientSnapshot;
import com.zxplat.domain.mrms.caserecord.vo.ProcedureLineSnapshot;
import com.zxplat.domain.mrms.caserecord.vo.TransferLineSnapshot;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordHomepageRepository;
import com.zxplat.domain.share.constants.MrmsConstants;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.zxplat.domain.mrms.caserecord.repository.HomepageBldRepository;
import com.zxplat.domain.mrms.caserecord.repository.HomepageDiagnosisRepository;
import com.zxplat.domain.mrms.caserecord.repository.HomepageIcuRepository;
import com.zxplat.domain.mrms.caserecord.repository.HomepageProcedureRepository;
import com.zxplat.domain.share.constants.MrmsErrorCodes;
import com.zxplat.domain.share.utils.FieldSourcesJsonUtil;
import com.zxplat.domain.share.utils.InpatientStayDaysCalculator;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页（{@code mrms_case_record_homepage}）聚合写侧领域服务。
 *
 * <p>职责：懒创建、人工保存、HIS 自动取值合并、子表全量对齐（软删旧行）、归档冻结/解档解冻。</p>
 * <p>读 HIS 快照经 {@link CaseRecordAutoFillSupport}（B.6.4：跨聚合读走各域 QueryService）。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordHomepageDomainService {

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;
    private final HomepageDiagnosisRepository diagnosisRepository;
    private final HomepageProcedureRepository procedureRepository;
    private final HomepageIcuRepository icuRepository;
    private final HomepageBldRepository bldRepository;
    private final CaseRecordAutoFillSupport caseRecordAutoFillSupport;
    private final CaseRecordIdempotentPersistSupport idempotentPersistSupport;
    private final ObjectMapper objectMapper;

    /**
     * 获取或创建首页；不存在时初始化空首页与 field_sources_json。
     *
     * <p>并发 bootstrap/loadPage 可能同时懒建同一 {@code case_record_id} 首页；后到者撞
     * {@code uk_mrms_homepage_case} 后回读，避免事务 aborted。</p>
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户主键（创建路径必填）
     * @param operatorId 操作人
     * @return 已存在或新建的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage ensureHomepage(Long caseRecordId, Long tenantId, Long operatorId) {
        // Pre：已有首页直接返回
        Optional<CaseRecordHomepage> existing =
                homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 懒创建路径须带租户
        if (tenantId == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "创建首页时租户不能为空");
        }
        // Cmd：空壳首页；并发撞唯一键则回读胜出方
        CaseRecordHomepage homepage = new CaseRecordHomepage();
        homepage.initEmpty(caseRecordId, tenantId, operatorId);
        try {
            // 独立事务 flush：并发撞 uk_mrms_homepage_case 时仅新建事务回滚
            return idempotentPersistSupport.insertHomepageNew(homepage);
        } catch (DataIntegrityViolationException integrityViolationException) {
            Optional<CaseRecordHomepage> raced =
                    homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
            if (raced.isPresent()) {
                return raced.get();
            }
            throw integrityViolationException;
        }
    }

    /**
     * 按病案主键加载已存在首页；不存在时 empty（只读，供质控等场景）。
     *
     * @param caseRecordId 病案主档主键
     * @return 首页实体或 empty
     */
    public Optional<CaseRecordHomepage> findOptionalByCaseRecordId(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
    }

    /**
     * 按病案主键加载已存在首页；不存在时抛业务异常。
     *
     * @param caseRecordId 病案主档主键
     * @return 首页实体
     */
    public CaseRecordHomepage requireExistingByCaseRecordId(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "首页不存在"));
    }

    /**
     * 保存首页标量与子表；子表非 null 时全量替换并标记 manual 来源。
     *
     * @param command 保存命令
     * @return 保存后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage save(HomepageSaveCommand command) {
        // Pre：校验主档存在
        var record = caseRecordRepository.findByIdAndIsDeletedFalse(command.getCaseRecordId())
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "病案主档不存在"));
        CaseRecordHomepage homepage = ensureHomepage(command.getCaseRecordId(), record.getTenantId(), command.getOperatorId());
        // 归档冻结后禁止保存
        homepage.assertEditable();
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        // Cmd：标量人工修订写入并 bump 版本
        homepage.applyManualFields(command, sources);
        // 客户端传入诊断子表时标记 manual 来源，后续自动取值不得覆盖
        if (command.getDiagnoses() != null) {
            sources.put("diagnoses", FieldFillSource.MANUAL.getCode());
        }
        // 客户端传入手术子表时标记 manual 来源，后续自动取值不得覆盖
        if (command.getProcedures() != null) {
            sources.put("procedures", FieldFillSource.MANUAL.getCode());
        }
        // 重症监护子表传入时标记 manual，后续自动取值不得覆盖
        if (command.getIcus() != null) {
            sources.put("icus", FieldFillSource.MANUAL.getCode());
        }
        // 输血子表传入时标记 manual，后续自动取值不得覆盖
        if (command.getBlds() != null) {
            sources.put("blds", FieldFillSource.MANUAL.getCode());
        }
        homepage.setFieldSourcesJson(FieldSourcesJsonUtil.toJson(objectMapper, sources));
        // 草稿期保存不升业务版本（与编辑审核版本解耦，DM-MRMS-001 v1.2）
        homepage.setUpdatedAt(java.time.LocalDateTime.now());
        homepage.setUpdatedBy(command.getOperatorId());
        // 持久化首页标量
        homepageRepository.save(homepage);
        // 诊断子表非 null 时全量替换（null 表示本次不修改子表）
        if (command.getDiagnoses() != null) {
            replaceDiagnoses(command.getCaseRecordId(), homepage.getTenantId(), command.getDiagnoses());
        }
        // 手术子表非 null 时全量替换（null 表示本次不修改子表）
        if (command.getProcedures() != null) {
            replaceProcedures(command.getCaseRecordId(), homepage.getTenantId(), command.getProcedures());
        }
        // 重症监护子表全量替换
        if (command.getIcus() != null) {
            replaceIcus(command.getCaseRecordId(), homepage.getTenantId(), command.getIcus());
        }
        // 输血子表全量替换
        if (command.getBlds() != null) {
            replaceBlds(command.getCaseRecordId(), homepage.getTenantId(), command.getBlds());
        }
        return homepage;
    }

    /**
     * 从 HIS 读侧快照合并首页字段，尊重 {@code field_sources_json} 中 manual 标记。
     *
     * @param record 病案主档
     * @param sections 合并分区（{@code all/basic/inpatient/staff/diagnosis/procedure}；
     *                 {@code fee} 须显式传入，不包含在 {@code all} 内）
     * @param forceOverwrite 是否强制覆盖 manual 字段
     * @param operatorId 操作人
     * @return 合并后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage mergeAutoFillFromHis(
            CaseRecord record,
            List<String> sections,
            boolean forceOverwrite,
            Long operatorId) {
        // Cmd：加载首页与字段来源映射
        CaseRecordHomepage homepage = homepageRepository
                .findByCaseRecordIdAndIsDeletedFalse(record.getId())
                .orElseThrow();
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        boolean all = sections != null && sections.contains("all");
        // 按分区合并标量与子表
        // 患者基础信息分区：姓名、证件、联系方式等
        if (all || containsSection(sections, "basic")) {
            mergeBasicSection(record, homepage, sources, forceOverwrite);
        }
        // 住院/出入院信息分区：入出院科别与时间
        if (all || containsSection(sections, "inpatient")) {
            mergeInpatientSection(record, homepage, sources, forceOverwrite);
        }
        // 参与人员分区：科主任、主治医师、护士等
        if (all || containsSection(sections, "staff")) {
            mergeStaffSection(record, homepage, sources, forceOverwrite);
        }
        // 诊断子表：尊重 manual 锁，实际写入后标记 auto 来源
        if (all || containsSection(sections, "diagnosis")) {
            if (mergeDiagnosisSection(record, homepage, forceOverwrite)) {
                sources.put("diagnoses", FieldFillSource.AUTO.getCode());
            }
        }
        // 手术子表：尊重 manual 锁，实际写入后标记 auto 来源
        if (all || containsSection(sections, "procedure")) {
            if (mergeProcedureSection(record, homepage, forceOverwrite)) {
                sources.put("procedures", FieldFillSource.AUTO.getCode());
            }
        }
        // 费用：仅显式 fee 段重算落库；「all / 全部导入」不得覆盖费用快照（须点「重新统计」）
        if (containsSection(sections, "fee")) {
            mergeFeeSection(record, homepage, sources, forceOverwrite);
        }
        homepage.setFieldSourcesJson(FieldSourcesJsonUtil.toJson(objectMapper, sources));
        homepage.setUpdatedAt(LocalDateTime.now());
        homepage.setUpdatedBy(operatorId);
        return homepageRepository.save(homepage);
    }

    /**
     * 首页是否已有费用快照（总额或分项 JSON 任一非空即视为已统计）。
     *
     * @param homepage 首页实体；可空
     * @return true 表示打开页应只读快照、不得按目录现查覆盖
     */
    public boolean hasFeeSnapshot(CaseRecordHomepage homepage) {
        if (homepage == null) {
            return false;
        }
        // 总额已落库
        if (homepage.getMedfeeSumamt() != null) {
            return true;
        }
        String itemsJson = trimToNull(homepage.getFeeItemsJson());
        // 分项 JSON 已落库
        if (itemsJson != null) {
            return true;
        }
        return false;
    }

    /**
     * 从首页实体还原费用快照 VO（供 DTO.fees / 4401 读侧）。
     *
     * @param homepage 首页实体
     * @return 快照；无快照时 null
     */
    public FeeSummarySnapshot readFeeSnapshot(CaseRecordHomepage homepage) {
        // 无实体或未统计：调用方走补种或空 fees
        if (!this.hasFeeSnapshot(homepage)) {
            return null;
        }
        FeeSummarySnapshot snap = new FeeSummarySnapshot();
        snap.setMedfeeSumamt(homepage.getMedfeeSumamt());
        snap.setSelfpayAmt(homepage.getSelfpayAmt());
        Map<String, BigDecimal> items = this.parseFeeItemsJson(homepage.getFeeItemsJson());
        snap.setFeeItems(items);
        return snap;
    }

    /**
     * 按当前 HIS 账单重算费用并落库（「重新统计」权威写路径）。
     *
     * <p>分项映射取目录<strong>当前</strong>{@code mr_settlement_type_code}；覆盖旧快照。</p>
     *
     * @param record     病案主档（取 encounterId）
     * @param homepage   首页实体
     * @param operatorId 操作人
     * @return 保存后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage refreshFeeSnapshot(
            CaseRecord record, CaseRecordHomepage homepage, Long operatorId) {
        // Gate：主档与首页必填
        if (record == null || homepage == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "重算费用快照时病案或首页不能为空");
        }
        // 归档冻结后禁止改费用快照
        homepage.assertEditable();
        // Pre：按当前目录结算类型汇总账单
        FeeSummarySnapshot snap = caseRecordAutoFillSupport.sumFeeSummary(record.getEncounterId());
        // Cmd：写入实体字段
        this.applyFeeSnapshotToEntity(homepage, snap);
        homepage.setUpdatedAt(LocalDateTime.now());
        homepage.setUpdatedBy(operatorId);
        return homepageRepository.save(homepage);
    }

    /**
     * 打开页/保存后：无费用快照时按当前账单补种；已有总额但缺自付时仅补自付（不覆盖分项）。
     *
     * @param record     病案主档
     * @param homepage   首页实体
     * @param operatorId 操作人
     * @return 可能已补种/补自付后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage ensureFeeSnapshotSeeded(
            CaseRecord record, CaseRecordHomepage homepage, Long operatorId) {
        // 冻结首页：只读，不补种
        if (homepage != null && Boolean.TRUE.equals(homepage.getFrozen())) {
            return homepage;
        }
        // 完全无快照：全量补种
        if (!this.hasFeeSnapshot(homepage)) {
            return this.refreshFeeSnapshot(record, homepage, operatorId);
        }
        // 存量常见：有总额无自付（V26 对医保留空）→ 只补 selfpay，不改分项
        if (homepage.getMedfeeSumamt() != null && homepage.getSelfpayAmt() == null) {
            return this.fillMissingSelfpayAmt(record, homepage, operatorId);
        }
        return homepage;
    }

    /**
     * 仅补写自付金额：按当前结算/医保回执解析；解析不到则保持空、不改其它费用列。
     *
     * @param record     病案主档
     * @param homepage   已有费用快照的首页
     * @param operatorId 操作人
     * @return 保存后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage fillMissingSelfpayAmt(
            CaseRecord record, CaseRecordHomepage homepage, Long operatorId) {
        // Gate：主档与首页必填
        if (record == null || homepage == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "补自付时病案或首页不能为空");
        }
        // 已有自付：幂等
        if (homepage.getSelfpayAmt() != null) {
            return homepage;
        }
        // Pre：现查汇总（只用其 selfpay；分项/总额不覆盖）
        FeeSummarySnapshot snap = caseRecordAutoFillSupport.sumFeeSummary(record.getEncounterId());
        // 仍解析不到：保持空，避免误写 0
        if (snap == null || snap.getSelfpayAmt() == null) {
            return homepage;
        }
        // Cmd：只落自付列
        homepage.setSelfpayAmt(MonetaryAmountSupport.scaleSettle(snap.getSelfpayAmt()));
        homepage.setUpdatedAt(LocalDateTime.now());
        homepage.setUpdatedBy(operatorId);
        return homepageRepository.save(homepage);
    }

    /**
     * 列出病案首页诊断子表（未软删，按 {@code sort_no} 升序）。
     * <p>与前端上移/下移维护的序号一致，避免按诊断类型重排导致保存后顺序回跳。
     *
     * @param caseRecordId 病案主档主键
     * @return 诊断行列表
     */
    public List<HomepageDiagnosis> listDiagnoses(Long caseRecordId) {
        return diagnosisRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 列出病案首页手术子表（未软删，按序号排序）。
     *
     * @param caseRecordId 病案主档主键
     * @return 手术行列表
     */
    public List<HomepageProcedure> listProcedures(Long caseRecordId) {
        return procedureRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 列出病案首页重症监护子表（未软删，按序号排序）。
     *
     * @param caseRecordId 病案主档主键
     * @return ICU 行列表
     */
    public List<HomepageIcu> listIcus(Long caseRecordId) {
        return icuRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 列出病案首页输血子表（未软删，按序号排序）。
     *
     * @param caseRecordId 病案主档主键
     * @return 输血行列表
     */
    public List<HomepageBld> listBlds(Long caseRecordId) {
        return bldRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 自动取值路径：软删后按快照行落库诊断行。
     */
    private void replaceDiagnosisSnapshots(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
        softDeleteDiagnoses(caseRecordId);
        // 无新行时仅软删旧行即可
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 遍历快照行补齐外键与审计字段后批量落库
        for (HomepageDiagnosis line : lines) {
            line.setCaseRecordId(caseRecordId);
            line.setTenantId(tenantId);
            line.setValiFlag("1");
            line.setCreatedAt(LocalDateTime.now());
            line.setIsDeleted(false);
        }
        diagnosisRepository.saveAll(lines);
    }

    /**
     * 自动取值路径：软删后按快照行落库手术行。
     */
    private void replaceProcedureSnapshots(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
        softDeleteProcedures(caseRecordId);
        // 无新行时仅软删旧行即可
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 遍历快照行补齐外键与审计字段后批量落库
        for (HomepageProcedure line : lines) {
            line.setCaseRecordId(caseRecordId);
            line.setTenantId(tenantId);
            line.setValiFlag("1");
            line.setCreatedAt(LocalDateTime.now());
            line.setIsDeleted(false);
        }
        procedureRepository.saveAll(lines);
    }

    /**
     * 归档冻结首页。
     *
     * @param caseRecordId 病案主档主键
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long caseRecordId, Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElse(null);
        // 无首页时跳过
        if (homepage == null) {
            return;
        }
        homepage.freezeHomepage(operatorId);
        // 持久化
        homepageRepository.save(homepage);
    }

    /**
     * 编辑审核通过后回写首页（保持 frozen；设定 toVersion；不覆盖费用分区）。
     *
     * <p>入参 {@link HomepageSaveCommand} 由应用层从工作稿 JSON 解析；本方法不解析 JSON。</p>
     *
     * @param command 首页保存命令（含诊断/手术子表可选）
     * @param expectedFromVersion 申请上的 from_version，须等于当前首页 version_no
     * @param toVersion 目标版本
     * @return 回写后首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage applyApprovedEditSnapshot(
            HomepageSaveCommand command,
            Integer expectedFromVersion,
            Integer toVersion) {
        // Pre：加载首页并校验版本并发
        CaseRecordHomepage homepage = homepageRepository
                .findByCaseRecordIdAndIsDeletedFalse(command.getCaseRecordId())
                .orElseThrow(() -> new EntityNotFoundException("病案首页", command.getCaseRecordId()));
        if (expectedFromVersion == null || !expectedFromVersion.equals(homepage.getVersionNo())) {
            throw new BusinessException(
                    MrmsErrorCodes.HOMEPAGE_EDIT_VERSION_CONFLICT,
                    "首页版本已变化，请重新申请编辑");
        }
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        // Cmd：允许在 frozen 下回写非费用字段（跳过 assertEditable）
        homepage.applyManualFields(command, sources);
        // 审核落地诊断子表：标记 manual，防止后续 autoFill 覆盖审批稿
        if (command.getDiagnoses() != null) {
            sources.put("diagnoses", FieldFillSource.MANUAL.getCode());
        }
        // 审核落地手术子表：标记 manual
        if (command.getProcedures() != null) {
            sources.put("procedures", FieldFillSource.MANUAL.getCode());
        }
        // 审核落地重症监护子表：标记 manual
        if (command.getIcus() != null) {
            sources.put("icus", FieldFillSource.MANUAL.getCode());
        }
        // 审核落地输血子表：标记 manual
        if (command.getBlds() != null) {
            sources.put("blds", FieldFillSource.MANUAL.getCode());
        }
        homepage.setFieldSourcesJson(FieldSourcesJsonUtil.toJson(objectMapper, sources));
        homepage.applyApprovedVersion(toVersion, command.getOperatorId());
        homepageRepository.save(homepage);
        // 诊断子表非 null：全量 replace 审批稿行
        if (command.getDiagnoses() != null) {
            replaceDiagnoses(command.getCaseRecordId(), homepage.getTenantId(), command.getDiagnoses());
        }
        // 手术子表非 null：全量 replace 审批稿行
        if (command.getProcedures() != null) {
            replaceProcedures(command.getCaseRecordId(), homepage.getTenantId(), command.getProcedures());
        }
        // 重症监护子表非 null：全量 replace 审批稿行
        if (command.getIcus() != null) {
            replaceIcus(command.getCaseRecordId(), homepage.getTenantId(), command.getIcus());
        }
        // 输血子表非 null：全量 replace 审批稿行
        if (command.getBlds() != null) {
            replaceBlds(command.getCaseRecordId(), homepage.getTenantId(), command.getBlds());
        }
        return homepage;
    }

    /**
     * 解档解冻首页。
     *
     * @param caseRecordId 病案主档主键
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long caseRecordId, Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElse(null);
        // 无首页时跳过
        if (homepage == null) {
            return;
        }
        homepage.unfreezeHomepage(operatorId);
        // 持久化
        homepageRepository.save(homepage);
    }

    /**
     * 人工保存路径：软删旧诊断行后按入参全量重建（含病情/确诊/说明/诊断医生/上传扩列）。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId     租户主键
     * @param lines        入参诊断行；null/空则仅软删
     */
    private void replaceDiagnoses(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
        softDeleteDiagnoses(caseRecordId);
        // 无新行：仅软删后返回
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<HomepageDiagnosis> toSave = new ArrayList<>();
        // 全量重建：不复用旧 id，避免与软删行冲突
        for (HomepageDiagnosis line : lines) {
            HomepageDiagnosis entity = new HomepageDiagnosis();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setDiagTypeCode(line.getDiagTypeCode());
            entity.setSortNo(line.getSortNo());
            entity.setDiagCode(line.getDiagCode());
            entity.setDiagName(line.getDiagName());
            entity.setAdmCondCode(line.getAdmCondCode());
            entity.setOutIllStatusCode(line.getOutIllStatusCode());
            entity.setConfirmTime(line.getConfirmTime());
            entity.setDiagRemark(line.getDiagRemark());
            // 出院诊断扩列：诊断医生主键+姓名、是否上送医保
            entity.setConfirmDrId(line.getConfirmDrId());
            entity.setConfirmDrName(line.getConfirmDrName());
            entity.setNeedYbUp(line.getNeedYbUp());
            entity.setMaindiagFlag(line.getMaindiagFlag());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        diagnosisRepository.saveAll(toSave);
    }

    /**
     * 人工保存路径：软删旧行后重建手术子表（含 P0 扩列字段全量复制）。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId     租户主键
     * @param lines        入参手术行；空则仅软删
     */
    private void replaceProcedures(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
        softDeleteProcedures(caseRecordId);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<HomepageProcedure> toSave = new ArrayList<>();
        for (HomepageProcedure line : lines) {
            HomepageProcedure entity = new HomepageProcedure();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setSortNo(line.getSortNo());
            entity.setOprnOprtCode(line.getOprnOprtCode());
            entity.setOprnOprtName(line.getOprnOprtName());
            entity.setOprnDate(line.getOprnDate());
            entity.setOprnTime(line.getOprnTime());
            entity.setMainOprnFlag(line.getMainOprnFlag());
            // 手术扩列：级别/切口/愈合/麻醉/人员/起止/样本备注（DM-MRMS-001 §3.2）
            entity.setOprnLevelCode(line.getOprnLevelCode());
            entity.setOprnIncisionCode(line.getOprnIncisionCode());
            entity.setHealGradeCode(line.getHealGradeCode());
            entity.setAnstWayCode(line.getAnstWayCode());
            entity.setOperDrName(line.getOperDrName());
            entity.setOperDrCode(line.getOperDrCode());
            entity.setAsit1Name(line.getAsit1Name());
            entity.setAsit2Name(line.getAsit2Name());
            entity.setNurseName(line.getNurseName());
            entity.setAnstDrName(line.getAnstDrName());
            entity.setAnstDrCode(line.getAnstDrCode());
            entity.setAnstStartTime(line.getAnstStartTime());
            entity.setAnstEndTime(line.getAnstEndTime());
            entity.setSpecimen(line.getSpecimen());
            entity.setNote(line.getNote());
            entity.setSendLabFlag(line.getSendLabFlag());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        procedureRepository.saveAll(toSave);
    }

    /** 子表全量对齐：软删当前有效诊断行。 */
    private void softDeleteDiagnoses(Long caseRecordId) {
        List<HomepageDiagnosis> existing = diagnosisRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageDiagnosis row : existing) {
            row.setIsDeleted(true);
        }
        diagnosisRepository.saveAll(existing);
    }

    /** 子表全量对齐：软删当前有效手术行。 */
    private void softDeleteProcedures(Long caseRecordId) {
        List<HomepageProcedure> existing = procedureRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageProcedure row : existing) {
            row.setIsDeleted(true);
        }
        procedureRepository.saveAll(existing);
    }

    /**
     * 人工保存路径：软删旧行后重建重症监护子表。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId     租户主键
     * @param lines        入参 ICU 行；空则仅软删
     */
    private void replaceIcus(Long caseRecordId, Long tenantId, List<HomepageIcu> lines) {
        // 前置：软删旧行，保证全量对齐后库内仅保留本批有效行
        softDeleteIcus(caseRecordId);
        // 空列表=清空 ICU 子表，不再重建
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 组装：拷贝业务字段并补齐外键/审计，禁止复用入参实体 id（全量重建）
        List<HomepageIcu> toSave = new ArrayList<>();
        for (HomepageIcu line : lines) {
            HomepageIcu entity = new HomepageIcu();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setSortNo(line.getSortNo());
            entity.setScsCutdWardType(line.getScsCutdWardType());
            entity.setScsCutdInpoolTime(line.getScsCutdInpoolTime());
            entity.setScsCutdExitTime(line.getScsCutdExitTime());
            entity.setScsCutdSumDura(line.getScsCutdSumDura());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        // 持久化：批量落库本批 ICU 行
        icuRepository.saveAll(toSave);
    }

    /**
     * 人工保存路径：软删旧行后重建输血子表。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId     租户主键
     * @param lines        入参输血行；空则仅软删
     */
    private void replaceBlds(Long caseRecordId, Long tenantId, List<HomepageBld> lines) {
        // 前置：软删旧行，保证全量对齐后库内仅保留本批有效行
        softDeleteBlds(caseRecordId);
        // 空列表=清空输血子表，不再重建
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 组装：拷贝品种/量/单位并补齐外键/审计，禁止复用入参实体 id
        List<HomepageBld> toSave = new ArrayList<>();
        for (HomepageBld line : lines) {
            HomepageBld entity = new HomepageBld();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setSortNo(line.getSortNo());
            entity.setBldCat(line.getBldCat());
            entity.setBldAmt(line.getBldAmt());
            entity.setBldUnt(line.getBldUnt());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        // 持久化：批量落库本批输血行
        bldRepository.saveAll(toSave);
    }

    /**
     * 子表全量对齐：软删当前有效 ICU 行（不物理删除，供 replace 重建）。
     *
     * @param caseRecordId 病案主档主键
     */
    private void softDeleteIcus(Long caseRecordId) {
        List<HomepageIcu> existing = icuRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        // 逐行打软删标记，保留历史行供审计追溯
        for (HomepageIcu row : existing) {
            row.setIsDeleted(true);
        }
        icuRepository.saveAll(existing);
    }

    /**
     * 子表全量对齐：软删当前有效输血行（不物理删除，供 replace 重建）。
     *
     * @param caseRecordId 病案主档主键
     */
    private void softDeleteBlds(Long caseRecordId) {
        List<HomepageBld> existing = bldRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        // 逐行打软删标记，保留历史行供审计追溯
        for (HomepageBld row : existing) {
            row.setIsDeleted(true);
        }
        bldRepository.saveAll(existing);
    }

    /** 合并患者基础信息分区（adm_patient）。 */
    private void mergeBasicSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        PatientSnapshot patient = caseRecordAutoFillSupport.loadPatient(record.getPatientId());
        // HIS 无患者快照时跳过本分区
        if (patient == null) {
            return;
        }
        putIfAllowed(homepage, sources, "psn_name", patient.getName(), force);
        putIfAllowed(homepage, sources, "gend_code", patient.getGenderCode(), force);
        putIfAllowed(homepage, sources, "brdy", patient.getBirthDate(), force);
        putIfAllowed(homepage, sources, "certno", patient.getCertno(), force);
        putIfAllowed(homepage, sources, "curr_addr", patient.getCurrAddr(), force);
        putIfAllowed(homepage, sources, "curr_addr_codes", patient.getCurrAddrCodes(), force);
        putIfAllowed(homepage, sources, "curr_addr_detail", patient.getCurrAddrDetail(), force);
        putIfAllowed(homepage, sources, "psn_tel", patient.getPhone(), force);
        putIfAllowed(homepage, sources, "coner_name", patient.getContactName(), force);
        putIfAllowed(homepage, sources, "coner_tel", patient.getContactPhone(), force);
        putIfAllowed(homepage, sources, "coner_rlts_code", patient.getContactRelationCode(), force);
        putIfAllowed(homepage, sources, "ntly_code", patient.getNationalityCode(), force);
        putIfAllowed(homepage, sources, "naty_code", patient.getNatyCode(), force);
        putIfAllowed(homepage, sources, "mrg_stas_code", patient.getMaritalStatusCode(), force);
        putIfAllowed(homepage, sources, "prfs_code", patient.getPrfsCode(), force);
        putIfAllowed(homepage, sources, "napl_codes", patient.getNaplCodes(), force);
        // 工作单位写入正式列 empr_addr
        putIfAllowed(homepage, sources, "empr_addr", patient.getWorkCompany(), force);
    }

    /** 合并住院信息分区（adm_encounter + 账户付费方式 + 转科摘要）。 */
    private void mergeInpatientSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        EncounterSnapshot enc = caseRecordAutoFillSupport.loadEncounter(record.getEncounterId());
        // HIS 无就诊快照时跳过本分区
        if (enc == null) {
            return;
        }
        putIfAllowed(homepage, sources, "ipt_no", enc.getIptNo(), force);
        putIfAllowed(homepage, sources, "adm_time", enc.getAdmTime(), force);
        putIfAllowed(homepage, sources, "dscg_time", enc.getDscgTime(), force);
        // 纠偏非法/空白入院途径后，按就诊 in_way（与 adm_way 同码）回填；空白/非法不受 manual 空锁阻挡
        this.clearInvalidAdmWayIfNeeded(homepage, sources);
        this.putAdmWayFromHis(homepage, sources, enc.getAdmWayCode(), force);
        // 付费方式：入院登记费用性质/险种已知，空或 auto 时带入，尊重 manual
        putIfAllowed(homepage, sources, "pay_way_code", enc.getPayWayCode(), force);
        putIfAllowed(homepage, sources, "adm_caty_code", enc.getAdmCatyCode(), force);
        putIfAllowed(homepage, sources, "adm_caty_name", enc.getAdmCatyName(), force);
        putIfAllowed(homepage, sources, "dscg_caty_code", enc.getDscgCatyCode(), force);
        putIfAllowed(homepage, sources, "dscg_caty_name", enc.getDscgCatyName(), force);
        // 病区床位落正式列，与前端 admWard/admBed 对齐
        putIfAllowed(homepage, sources, "adm_ward", enc.getAdmWardName(), force);
        putIfAllowed(homepage, sources, "adm_bed", enc.getAdmBedName(), force);
        putIfAllowed(homepage, sources, "dscg_ward", enc.getDscgWardName(), force);
        putIfAllowed(homepage, sources, "dscg_bed", enc.getDscgBedName(), force);
        // 出入院时间齐全时计算住院天数（即进不计出：入院日计、出院日不计；同日至少 1）
        if (enc.getAdmTime() != null && enc.getDscgTime() != null) {
            Integer iptDays = InpatientStayDaysCalculator.calcDischargedStayDays(
                    enc.getAdmTime(), enc.getDscgTime());
            putIfAllowed(homepage, sources, "ipt_days", iptDays, force);
        }
        // 护理天数：护理级别医嘱汇总
        NursingCareDaysSnapshot nursing =
                caseRecordAutoFillSupport.sumNursingCareDays(record.getEncounterId(), enc.getDscgTime());
        putIfAllowed(homepage, sources, "spga_nurscare_days", nursing.getSpgaNurscareDays(), force);
        putIfAllowed(homepage, sources, "lv1_nurscare_days", nursing.getLv1NurscareDays(), force);
        putIfAllowed(homepage, sources, "scd_nurscare_days", nursing.getScdNurscareDays(), force);
        putIfAllowed(homepage, sources, "lv3_nurscare_days", nursing.getLv3NurscareDays(), force);
        putIfAllowed(
                homepage,
                sources,
                "dscg_way_code",
                caseRecordAutoFillSupport.resolveDischargeWayCode(record.getEncounterId()),
                force);
        List<TransferLineSnapshot> transfers = caseRecordAutoFillSupport.listTransfers(record.getEncounterId());
        // 有转科里程碑时拼接科室路径摘要（A→B→C）
        if (transfers != null && !transfers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (TransferLineSnapshot line : transfers) {
                if (sb.length() > 0) {
                    sb.append("→");
                }
                sb.append(line.getToDeptName());
            }
            putIfAllowed(homepage, sources, "transfer_dept_summary", sb.toString(), force);
        }
    }

    /** 合并参与人员分区（就诊参与者）。 */
    private void mergeStaffSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        EncounterSnapshot enc = caseRecordAutoFillSupport.loadEncounter(record.getEncounterId());
        // HIS 无就诊快照时跳过本分区
        if (enc == null) {
            return;
        }
        putIfAllowed(homepage, sources, "dept_director_name", enc.getDeptDirectorName(), force);
        putIfAllowed(homepage, sources, "chfdr_name", enc.getChfdrName(), force);
        putIfAllowed(homepage, sources, "atddr_name", enc.getAtddrName(), force);
        putIfAllowed(homepage, sources, "atddr_practitioner_id", enc.getAtddrPractitionerId(), force);
        putIfAllowed(homepage, sources, "ipt_dr_name", enc.getIptDrName(), force);
        putIfAllowed(homepage, sources, "resp_nurs_name", enc.getRespNursName(), force);
    }

    /**
     * 费用分区：按当前账单与目录病案结算类型重算并写入首页费用快照列。
     *
     * <p>目录 {@code mr_settlement_type_code} 变更后，仅本分区（「重新统计」）会覆盖快照；
     * 打开页读 {@link #readFeeSnapshot}，不点则不变。</p>
     *
     * @param record   病案
     * @param homepage 首页（写入 medfee/selfpay/fee_items_json）
     * @param sources  字段来源（标记 fee 段为 auto）
     * @param force    强制覆盖标志（费用无 manual 锁，忽略）
     */
    private void mergeFeeSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        // Pre：现查汇总（仅写库路径调用，非打开页只读）
        FeeSummarySnapshot snap = caseRecordAutoFillSupport.sumFeeSummary(record.getEncounterId());
        // Cmd：覆盖快照列
        this.applyFeeSnapshotToEntity(homepage, snap);
        // Asm：标记费用段为自动取值，便于审计
        if (sources != null) {
            sources.put("fee", FieldFillSource.AUTO.getCode());
        }
    }

    /**
     * 将费用汇总 VO 写入首页实体（结算刻度 2 位；分项 JSON）。
     *
     * @param homepage 首页实体
     * @param snap     汇总；null 时清零总额并写空分项对象
     */
    private void applyFeeSnapshotToEntity(CaseRecordHomepage homepage, FeeSummarySnapshot snap) {
        if (homepage == null) {
            return;
        }
        // 无汇总：总额/自付置 0，分项写齐 28 键为 0，避免 null 被当成「未统计」
        if (snap == null) {
            homepage.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
            homepage.setSelfpayAmt(null);
            homepage.setFeeItemsJson(this.toFeeItemsJson(new LinkedHashMap<>()));
            return;
        }
        BigDecimal medfee = snap.getMedfeeSumamt();
        if (medfee == null) {
            medfee = BigDecimal.ZERO;
        }
        homepage.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(medfee));
        // 自付可空（未结算/无回执）
        if (snap.getSelfpayAmt() == null) {
            homepage.setSelfpayAmt(null);
        } else {
            homepage.setSelfpayAmt(MonetaryAmountSupport.scaleSettle(snap.getSelfpayAmt()));
        }
        Map<String, BigDecimal> items = snap.getFeeItems();
        if (items == null) {
            items = new LinkedHashMap<>();
        }
        // 分项金额统一结算刻度后再序列化
        Map<String, BigDecimal> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : items.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            BigDecimal amt = entry.getValue();
            if (amt == null) {
                amt = BigDecimal.ZERO;
            }
            scaled.put(entry.getKey(), MonetaryAmountSupport.scaleSettle(amt));
        }
        homepage.setFeeItemsJson(this.toFeeItemsJson(scaled));
    }

    /**
     * 分项 Map → JSONB 文本。
     *
     * @param items 分项；可空
     * @return JSON 对象文本；序列化失败时抛业务异常
     */
    private String toFeeItemsJson(Map<String, BigDecimal> items) {
        try {
            Map<String, BigDecimal> safe = items;
            if (safe == null) {
                safe = new LinkedHashMap<>();
            }
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_SUBMIT_VALIDATION, "费用分项快照序列化失败");
        }
    }

    /**
     * JSONB 文本 → 分项 Map。
     *
     * @param json 列存 JSON；空白返回空 Map
     * @return 分项 Map
     */
    private Map<String, BigDecimal> parseFeeItemsJson(String json) {
        String text = trimToNull(json);
        if (text == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, BigDecimal> parsed =
                    objectMapper.readValue(text, new TypeReference<LinkedHashMap<String, BigDecimal>>() {
                    });
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            return parsed;
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_SUBMIT_VALIDATION, "费用分项快照解析失败");
        }
    }

    /**
     * 首页自动填充：合并出院/入院诊断子表；manual 锁定且非 force 时跳过。
     *
     * @param record   病案
     * @param homepage 首页快照
     * @param force    是否强制覆盖 manual 段
     * @return true 表示已写入
     */
    private boolean mergeDiagnosisSection(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        // 诊断子表已 manual 锁定且非强制覆盖时跳过
        if (!force && isManualSection(homepage, "diagnoses")) {
            return false;
        }
        List<HomepageDiagnosis> lines = new ArrayList<>();
        appendDiagLines(
                lines,
                HomepageDiagType.DISCHARGE_MAIN,
                caseRecordAutoFillSupport.listDischargeDiagnoses(record.getEncounterId()),
                true);
        appendDiagLines(
                lines,
                HomepageDiagType.DISCHARGE_OTHER,
                caseRecordAutoFillSupport.listDischargeDiagnoses(record.getEncounterId()),
                false);
        appendDiagLines(
                lines,
                HomepageDiagType.ADMISSION_MAIN,
                caseRecordAutoFillSupport.listAdmissionDiagnoses(record.getEncounterId()),
                true);
        appendDiagLines(
                lines,
                HomepageDiagType.ADMISSION_OTHER,
                caseRecordAutoFillSupport.listAdmissionDiagnoses(record.getEncounterId()),
                false);
        replaceDiagnosisSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    /**
     * 首页自动填充：合并手术子表；manual 锁定且非 force 时跳过。
     *
     * @param record   病案
     * @param homepage 首页快照
     * @param force    是否强制覆盖 manual 段
     * @return true 表示已写入
     */
    private boolean mergeProcedureSection(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        // 手术子表已 manual 锁定且非强制覆盖时跳过
        if (!force && isManualSection(homepage, "procedures")) {
            return false;
        }
        List<ProcedureLineSnapshot> procLines =
                caseRecordAutoFillSupport.listProcedures(record.getEncounterId());
        List<HomepageProcedure> lines = new ArrayList<>();
        int sort = 0;
        for (ProcedureLineSnapshot snap : procLines) {
            HomepageProcedure row = new HomepageProcedure();
            row.setSortNo(sort++);
            row.setOprnOprtCode(snap.getOprnCode());
            row.setOprnOprtName(snap.getOprnName());
            row.setOprnDate(snap.getOprnDate());
            row.setOprnTime(snap.getOprnTime());
            // 主手术标记：快照 main=true 写 1，否则 0
            if (Boolean.TRUE.equals(snap.getMain())) {
                row.setMainOprnFlag("1");
            } else {
                row.setMainOprnFlag("0");
            }
            row.setOprnLevelCode(snap.getOprnLevelCode());
            row.setOprnIncisionCode(snap.getOprnIncisionCode());
            row.setAnstWayCode(snap.getAnstWayCode());
            row.setOperDrName(snap.getOperDrName());
            row.setAsit1Name(snap.getAsit1Name());
            row.setAsit2Name(snap.getAsit2Name());
            row.setNurseName(snap.getNurseName());
            row.setAnstDrName(snap.getAnstDrName());
            row.setAnstStartTime(snap.getAnstStartTime());
            row.setAnstEndTime(snap.getAnstEndTime());
            row.setSpecimen(snap.getSpecimen());
            row.setNote(snap.getNote());
            row.setSendLabFlag(snap.getSendLabFlag());
            // heal_grade_code 仅首页终改，带入留空
            lines.add(row);
        }
        replaceProcedureSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    /** 将 HIS 诊断快照追加为首页诊断行（主/次诊断分流）。 */
    private void appendDiagLines(
            List<HomepageDiagnosis> target,
            HomepageDiagType type,
            List<DiagnosisLineSnapshot> snaps,
            boolean mainOnly) {
        if (snaps == null) {
            return;
        }
        int sort = 0;
        for (DiagnosisLineSnapshot snap : snaps) {
            // 主/次诊断分流：mainOnly 时跳过非主诊断行
            if (mainOnly && !Boolean.TRUE.equals(snap.getMain())) {
                continue;
            }
            // 次诊断分流：跳过主诊断行
            if (!mainOnly && Boolean.TRUE.equals(snap.getMain())) {
                continue;
            }
            HomepageDiagnosis row = new HomepageDiagnosis();
            row.setDiagTypeCode(type.getCode());
            row.setSortNo(sort++);
            row.setDiagCode(snap.getDiagCode());
            row.setDiagName(snap.getDiagName());
            row.setAdmCondCode(snap.getAdmCondCode());
            if (Boolean.TRUE.equals(snap.getMain())) {
                row.setMaindiagFlag("1");
            } else {
                row.setMaindiagFlag("0");
            }
            target.add(row);
        }
    }

    /** 判断子表/分区是否被人工编辑锁定。 */
    private boolean isManualSection(CaseRecordHomepage homepage, String sectionKey) {
        if (homepage == null) {
            return false;
        }
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        return FieldFillSource.MANUAL.getCode().equals(sources.get(sectionKey));
    }

    /**
     * 清空非法入院途径（如误写入的入院类型码 6=常规），并解除空白上的 manual 锁，便于按 in_way 回填。
     *
     * @param homepage 首页实体
     * @param sources  字段来源图
     */
    private void clearInvalidAdmWayIfNeeded(CaseRecordHomepage homepage, Map<String, String> sources) {
        String current = homepage.getAdmWayCode();
        // 空白：去掉 manual 空锁，后续可按 HIS 回填
        if (current == null || current.isBlank()) {
            sources.remove("adm_way_code");
            homepage.setAdmWayCode(null);
            return;
        }
        // 已是合法 4401 码则保留
        if (MrmsConstants.isValidAdmWayCode(current)) {
            return;
        }
        // 非法码（含历史 6=常规）：一律清空，避免前端 sanitize 后「有值不回显」
        homepage.setAdmWayCode(null);
        sources.remove("adm_way_code");
    }

    /**
     * 从就诊快照写入入院途径：合法 HIS 码回填；已有合法手工值且非 force 时保留。
     *
     * <p>调用关系：{@link #mergeInpatientSection} 在 {@link #clearInvalidAdmWayIfNeeded} 之后调用；
     * HIS 侧码来自 {@code EncounterSnapshot.admWayCode}（已由 {@code mapInWayCodeToAdmWayCode} 映射）。</p>
     *
     * @param homepage 首页实体
     * @param sources  字段来源图
     * @param fromHis  就诊映射后的 adm_way（1/2/3/9）
     * @param force    是否强制覆盖合法 manual
     */
    private void putAdmWayFromHis(
            CaseRecordHomepage homepage, Map<String, String> sources, String fromHis, boolean force) {
        // HIS 无入院途径时不覆盖首页（可能仍为空待手工选）
        if (fromHis == null || fromHis.isBlank()) {
            return;
        }
        // 非法 HIS 码不写入，避免再次污染 adm_way_code
        if (!MrmsConstants.isValidAdmWayCode(fromHis)) {
            return;
        }
        String current = homepage.getAdmWayCode();
        boolean currentOk = current != null && !current.isBlank() && MrmsConstants.isValidAdmWayCode(current);
        String source = sources.get("adm_way_code");
        // 已有合法手工值且非强制：尊重病案室选择
        if (!force
                && currentOk
                && FieldFillSource.MANUAL.getCode().equals(source)) {
            return;
        }
        applyAutoFillField(homepage, "adm_way_code", fromHis.trim());
        sources.put("adm_way_code", FieldFillSource.AUTO.getCode());
    }

    /**
     * 按 field_sources 与 force 决定是否写入标量并标记 auto。
     *
     * @param homepage 首页实体
     * @param sources  字段来源图
     * @param fieldKey 来源键（与列名/snake 键一致）
     * @param newVal   HIS 新值；null 表示不覆盖
     * @param force    是否强制覆盖 manual
     */
    private void putIfAllowed(
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            String fieldKey,
            Object newVal,
            boolean force) {
        // 源值为空时不覆盖首页字段
        if (newVal == null) {
            return;
        }
        String source = sources.get(fieldKey);
        // manual 锁定且非强制覆盖时保留医生/病案手工值
        if (!force && FieldFillSource.MANUAL.getCode().equals(source)) {
            return;
        }
        applyAutoFillField(homepage, fieldKey, newVal);
        sources.put(fieldKey, FieldFillSource.AUTO.getCode());
    }

    /**
     * 自动取值标量字段映射（switch 与首页列一一对应；未知 fieldKey 忽略）。
     *
     * @param homepage 首页实体
     * @param fieldKey 字段键
     * @param value    已判定非 null 的写入值
     */
    private void applyAutoFillField(CaseRecordHomepage homepage, String fieldKey, Object value) {
        switch (fieldKey) {
            case "psn_name" -> homepage.setPsnName(String.valueOf(value));
            case "gend_code" -> homepage.setGendCode(String.valueOf(value));
            case "brdy" -> homepage.setBrdy((LocalDate) value);
            case "certno" -> homepage.setCertno(String.valueOf(value));
            case "curr_addr" -> homepage.setCurrAddr(String.valueOf(value));
            case "curr_addr_codes" -> homepage.setCurrAddrCodes(String.valueOf(value));
            case "curr_addr_detail" -> homepage.setCurrAddrDetail(String.valueOf(value));
            case "psn_tel" -> homepage.setPsnTel(String.valueOf(value));
            case "coner_name" -> homepage.setConerName(String.valueOf(value));
            case "coner_tel" -> homepage.setConerTel(String.valueOf(value));
            case "coner_rlts_code" -> homepage.setConerRltsCode(normalizePatnRltsCode(value));
            case "ntly_code" -> homepage.setNtlyCode(String.valueOf(value));
            case "naty_code" -> homepage.setNatyCode(String.valueOf(value));
            case "mrg_stas_code" -> homepage.setMrgStasCode(String.valueOf(value));
            case "prfs_code" -> homepage.setPrfsCode(String.valueOf(value));
            case "napl_codes" -> homepage.setNaplCodes(String.valueOf(value));
            case "ipt_no" -> homepage.setIptNo(String.valueOf(value));
            case "adm_way_code" -> homepage.setAdmWayCode(String.valueOf(value));
            case "pay_way_code" -> homepage.setPayWayCode(String.valueOf(value));
            case "adm_time" -> homepage.setAdmTime((LocalDateTime) value);
            case "dscg_time" -> homepage.setDscgTime((LocalDateTime) value);
            case "adm_caty_code" -> homepage.setAdmCatyCode(String.valueOf(value));
            case "adm_caty_name" -> homepage.setAdmCatyName(String.valueOf(value));
            case "dscg_caty_code" -> homepage.setDscgCatyCode(String.valueOf(value));
            case "dscg_caty_name" -> homepage.setDscgCatyName(String.valueOf(value));
            case "ipt_days" -> homepage.setIptDays((Integer) value);
            case "spga_nurscare_days" -> homepage.setSpgaNurscareDays((Integer) value);
            case "lv1_nurscare_days" -> homepage.setLv1NurscareDays((Integer) value);
            case "scd_nurscare_days" -> homepage.setScdNurscareDays((Integer) value);
            case "lv3_nurscare_days" -> homepage.setLv3NurscareDays((Integer) value);
            case "dscg_way_code" -> homepage.setDscgWayCode(String.valueOf(value));
            case "transfer_dept_summary" -> homepage.setTransferDeptSummary(String.valueOf(value));
            case "dept_director_name" -> homepage.setDeptDirectorName(String.valueOf(value));
            case "chfdr_name" -> homepage.setChfdrName(String.valueOf(value));
            case "atddr_name" -> homepage.setAtddrName(String.valueOf(value));
            case "atddr_practitioner_id" -> homepage.setAtddrPractitionerId((Long) value);
            case "ipt_dr_name" -> homepage.setIptDrName(String.valueOf(value));
            case "resp_nurs_name" -> homepage.setRespNursName(String.valueOf(value));
            case "empr_addr" -> homepage.setEmprAddr(String.valueOf(value));
            case "adm_ward" -> homepage.setAdmWard(String.valueOf(value));
            case "adm_bed" -> homepage.setAdmBed(String.valueOf(value));
            case "dscg_ward" -> homepage.setDscgWard(String.valueOf(value));
            case "dscg_bed" -> homepage.setDscgBed(String.valueOf(value));
            default -> {
            }
        }
    }

    /** 判断合并请求是否包含指定分区 code。 */
    private boolean containsSection(List<String> sections, String code) {
        if (sections == null) {
            return false;
        }
        for (String section : sections) {
            if (code.equals(trimToNull(section))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 医保 patn_rlts 码规范化：主档 Integer 存 1/2，首页 VARCHAR 需 01/02。
     *
     * @param value 关系码（Integer 或已是字符串）
     * @return 补零后的两位及以上代码；空白原样返回
     */
    private static String normalizePatnRltsCode(Object value) {
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return raw;
        }
        if (raw.length() == 1 && Character.isDigit(raw.charAt(0))) {
            return "0" + raw;
        }
        return raw;
    }
}
