/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.hisproxy;

import com.openhis.elep.domain.prescription.command.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.command.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.result.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.command.ElepHisproxyRevokeCommand;
import com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult;
import com.openhis.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterContextResult;
import com.openhis.elep.domain.prescription.command.ElepReviewCommand;
import com.openhis.elep.domain.prescription.result.ElepReviewResult;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.command.ElepSignCommand;
import com.openhis.elep.domain.prescription.result.ElepSignResult;
import com.openhis.elep.domain.prescription.command.ElepCloneCommand;
import com.openhis.elep.domain.prescription.command.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.access.ElepPrescriptionOperations;
import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 电子处方 hisproxy（方案 1A：前端调 {@code /api/app/elepPrescription/*}）。
 * <p>注册只看 {@code openhis.modules.elep.enabled}；{@link ElepPrescriptionOperations}
 * 由 {@code openhis.elep.deployment.mode} 在 embedded Orchestrator 与 remote Client 之间二选一。
 * 不可用 {@code @ConditionalOnBean} 同时要求两个实现：多 name 为 AND，且与同扫描包内兄弟 Bean 存在注册时序问题。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
public class ElepPrescriptionAppService {

    private final ElepPrescriptionOperations elepPrescriptionOperations;

    /**
     * 合约是否支持电子处方。
     *
     * @param contractId 契约主键
     * @return 非空即支持
     */
    public boolean supportsContract(Long contractId) {
        // Gate：空契约视为不支持
        boolean supported = elepPrescriptionOperations.supportsContract(contractId);
        // Asm：布尔透传
        return supported;
    }

    /**
     * A0001 预核验：须已保存草稿且院内药师已审方。
     *
     * @param command 含 prescriptionId
     * @return 追溯码与可选 PDF
     */
    public ElepPreCheckResult preCheck(ElepPreCheckCommand command) {
        // Gate：未审方由编排拒绝
        // Pre：服务端按挂号上下文重装 A0001
        // Cmd：出站 A0001 并落 PRE_CHECKED
        ElepPreCheckResult result = elepPrescriptionOperations.preCheck(command);
        // Asm：返回追溯码
        return result;
    }

    /**
     * A0003 电子处方上传。
     *
     * @param command 已签名处方
     * @return 上传结果
     */
    public ElepFileUploadResult uploadFile(ElepFileUploadCommand command) {
        // Gate：须 SIGNED
        // Cmd：出站 A0003，带审方药师
        ElepFileUploadResult result = elepPrescriptionOperations.uploadFile(command);
        // Asm：回写 hiRxno
        return result;
    }

    /**
     * 本地 PDF 生成（预核验后、签名前）。
     *
     * @param command 处方与填模数据
     * @return PDF 路径
     */
    public ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command) {
        // Gate：须 PRE_CHECKED
        // Cmd：按 elep-contract 模板填 PDF
        ElepPdfGenerateResult result = elepPrescriptionOperations.generatePdf(command);
        // Asm：返回文件路径
        return result;
    }

    /**
     * A0002 医保电子签名。
     *
     * @param command 处方主键
     * @return 签名结果
     */
    public ElepSignResult sign(ElepSignCommand command) {
        // Gate：须已有 PDF
        // Cmd：出站 A0002
        ElepSignResult result = elepPrescriptionOperations.sign(command);
        // Asm：返回签名摘要
        return result;
    }

    /**
     * A0004 电子处方撤销。
     *
     * @param command 处方主键
     * @return 撤销结果
     */
    public ElepHisproxyRevokeResult revoke(ElepHisproxyRevokeCommand command) {
        // Gate：未撤销才可调
        // Cmd：出站 A0004
        ElepHisproxyRevokeResult result = elepPrescriptionOperations.revokeFromHisproxy(command);
        // Asm：本地标 REVOKED
        return result;
    }

    /**
     * 患者历史上传方列表（本期套用入口隐藏）。
     *
     * @param query 患者与分页
     * @return 历史页
     */
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        // Gate：无患者则空页
        ElepPatientHistoryResult result = elepPrescriptionOperations.listPatientHistory(query);
        // Asm：仅 UPLOADED
        return result;
    }

    /**
     * 当前就诊电子处方列表（含草稿/在途）。
     *
     * @param query 就诊主键
     * @return 列表
     */
    public List<ElepEncounterPrescriptionItem> listEncounterPrescriptions(ElepEncounterListQuery query) {
        // Gate：无就诊则空列表
        List<ElepEncounterPrescriptionItem> items = elepPrescriptionOperations.listEncounterPrescriptions(query);
        // Asm：按更新时间倒序
        return items;
    }

    /**
     * 处方详情（含快照结构化字段，不含密钥）。
     *
     * @param prescriptionId 主键
     * @return 详情
     */
    public ElepHistoryDetail getHistoryDetail(Long prescriptionId) {
        // Gate：主键必填由编排校验
        ElepHistoryDetail detail = elepPrescriptionOperations.getHistoryDetail(prescriptionId);
        // Asm：含审方与药品用法
        return detail;
    }

    /**
     * 套用/续方生成新草稿（本期前端隐藏）。
     *
     * @param command 源方与目标就诊
     * @return 新草稿
     */
    public ElepPrescriptionDraft cloneToEncounter(ElepCloneCommand command) {
        // Gate：仅已上传方可套用
        ElepPrescriptionDraft draft = elepPrescriptionOperations.cloneToEncounter(command);
        // Asm：返回新草稿号
        return draft;
    }

    /**
     * 保存/更新草稿（只写 yb_elep_*，须已有挂号 map(11)）。
     *
     * @param command 结构化诊断/药品
     * @return 草稿摘要
     */
    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command) {
        // Gate：就诊、患者由领域校验；省市工伤不选手选，契约由挂号 map 回填
        // Pre：编排内装载挂号上下文；医疗类别由病种有无决定
        // Cmd：独立开方落草稿并清审方字段
        ElepPrescriptionDraft draft = elepPrescriptionOperations.saveDraft(command);
        // Asm：直接返回领域摘要
        return draft;
    }

    /**
     * 装载当前就诊只读上下文，自费/无 mdtrt 时前端禁止开方。
     *
     * @param query 就诊、患者；契约可空，由挂号 map 回填
     * @return 上下文（含 contractBusNo，前端不选手选省市工伤）
     */
    public ElepEncounterContextResult loadEncounterContext(ElepEncounterContextQuery query) {
        // Gate：无就诊则领域返回 allowed=false
        // Pre：只读 HIS 与挂号 map(11)，契约由 map 回填，不写共享表
        ElepEncounterContextResult context = elepPrescriptionOperations.loadEncounterContext(query);
        // Asm：原样返回拦截原因与展示字段
        return context;
    }

    /**
     * 院内医保药师审方通过（未审不得预核验）。
     *
     * @param command 处方主键
     * @return 审方结果
     */
    public ElepReviewResult review(ElepReviewCommand command) {
        // Gate：仅草稿/失败可审
        // Pre：当前登录人须有药师医保码
        // Cmd：写入 phar_* / reviewed_at，不改 HIS
        ElepReviewResult result = elepPrescriptionOperations.review(command);
        // Asm：返回药师码与提示
        return result;
    }

    /**
     * A0005 平台处方信息查询。
     *
     * @param command 处方主键
     * @return 平台状态
     */
    public ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command) {
        // Gate：须有 hiRxno 或处方主键
        ElepPlatformQueryResult result = elepPrescriptionOperations.queryPlatform(command);
        // Asm：可选回写本地状态码
        return result;
    }

    /**
     * A0006 取药结果查询（本期前端不做）。
     *
     * @param command 医保处方号
     * @return 取药 JSON
     */
    public ElepPlatformMedResultResult queryMedResult(ElepPlatformMedResultQueryCommand command) {
        // Gate：hiRxno 必填
        ElepPlatformMedResultResult result = elepPrescriptionOperations.queryMedResultPlatform(command);
        // Asm：透传平台 JSON
        return result;
    }

    /**
     * 一键上传入口已废弃，须走分步。
     *
     * @param command 上传命令
     * @return 不会成功返回
     */
    public ElepUploadResult upload(ElepUploadCommand command) {
        // Gate：明确拒绝一键上传
        ElepUploadResult result = elepPrescriptionOperations.upload(command);
        return result;
    }

    /**
     * 领域撤销（A0004）。
     *
     * @param command 撤销命令
     * @return 撤销结果
     */
    public com.openhis.elep.domain.prescription.result.ElepRevokeResult revokePrescription(
            com.openhis.elep.domain.prescription.command.ElepRevokeCommand command) {
        // Cmd：出站 A0004
        com.openhis.elep.domain.prescription.result.ElepRevokeResult result =
                elepPrescriptionOperations.revoke(command);
        // Asm：成功则本地 REVOKED
        return result;
    }

    /**
     * 处方状态查询。
     *
     * @param command 查询命令
     * @return 查询结果
     */
    public ElepQueryResult query(ElepQueryCommand command) {
        // Cmd：出站 A0005
        ElepQueryResult result = elepPrescriptionOperations.query(command);
        return result;
    }

    /**
     * 取药结果查询（领域命令）。
     *
     * @param query 医保处方号
     * @return 取药结果
     */
    public ElepMedResultResult queryMedResult(ElepMedResultQuery query) {
        // Cmd：出站 A0006
        ElepMedResultResult result = elepPrescriptionOperations.queryMedResult(query);
        return result;
    }

    /**
     * 按处方组 ID 查已上传 hi_rxno。
     *
     * @param prescriptionGroupId 电子处方主键
     * @return 快照
     */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        // Gate：空 ID 则空
        Optional<ElepRxNoSnapshot> snapshot = elepPrescriptionOperations.findRxNoByPrescription(prescriptionGroupId);
        return snapshot;
    }

    /**
     * 按院内处方号查已上传 hi_rxno。
     *
     * @param prescriptionNo 院内处方号
     * @return 快照
     */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        // Gate：空号则空
        Optional<ElepRxNoSnapshot> snapshot = elepPrescriptionOperations.findRxNoByPrescriptionNo(prescriptionNo);
        return snapshot;
    }

    /**
     * 套用/续方至新就诊（Client）。
     *
     * @param command 源方与目标就诊
     * @return 新草稿
     */
    public ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        // Gate：仅已上传方可套用
        ElepPrescriptionDraft draft = elepPrescriptionOperations.cloneToNewEncounter(command);
        return draft;
    }
}
