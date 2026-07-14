/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.reconciliation.command.ApplyJilinClear3203ACommand;
import com.openhis.chs.domain.reconciliation.command.ListLocalReconRecordsCommand;
import com.openhis.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.openhis.chs.domain.reconciliation.result.LocalClearApplyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDailyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDetailResult;
import com.openhis.chs.domain.reconciliation.entity.YbClearApply;
import com.openhis.chs.domain.reconciliation.entity.YbReconDaily;
import com.openhis.chs.domain.reconciliation.entity.YbReconDetail;
import com.openhis.chs.domain.reconciliation.repository.YbClearApplyRepository;
import com.openhis.chs.domain.reconciliation.repository.YbReconDailyRepository;
import com.openhis.chs.domain.reconciliation.repository.YbReconDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 对账清算本地流水：{@code yb_clear_apply} / {@code yb_recon_daily} / {@code yb_recon_detail} */
@Service
@RequiredArgsConstructor
public class YbReconciliationDomainService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final YbClearApplyRepository clearApplyRepository;
    private final YbReconDailyRepository reconDailyRepository;
    private final YbReconDetailRepository reconDetailRepository;

    /**
     * 3203A 清算申请成功后落本地清算流水。
     *
     * @param contractId     契约主键
     * @param contractBusNo  契约业务编号
     * @param request        3203A 申请参数
     * @param clrAppyEvtId   平台返回的清算申请事件 ID
     * @param begndate       清算开始日期
     * @param enddate        清算结束日期
     * @return 持久化后的清算申请实体
     */
    @Transactional
    public YbClearApply saveAppliedClearApply(
            Long contractId,
            String contractBusNo,
            ApplyJilinClear3203ACommand request,
            String clrAppyEvtId,
            LocalDate begndate,
            LocalDate enddate) {
        YbClearApply row = new YbClearApply();
        row.setContractId(contractId);
        row.setContractBusNo(contractBusNo);
        row.setClrType(request.getClrType());
        row.setClrOptins(request.getClrOptins());
        row.setBegndate(begndate);
        row.setEnddate(enddate);
        row.setMedfeeSumamt(request.getMedfeeSumamt());
        row.setMedSumfee(request.getMedSumfee());
        row.setFundAppySum(request.getFundAppySum());
        row.setCashPayamt(request.getCashPayamt());
        row.setAcctPay(request.getAcctPay());
        row.setClrAppyEvtId(clrAppyEvtId);
        row.setClrStas("10");
        row.setStatus(YbClearApply.STATUS_APPLIED);
        return clearApplyRepository.saveAndFlush(row);
    }

    /**
     * 3204A 撤销成功后标记本地清算申请为已撤销。
     *
     * @param clrAppyEvtId 清算申请事件 ID
     * @param tenantId     租户
     */
    @Transactional
    public void markClearApplyRevoked(String clrAppyEvtId, Long tenantId) {
        clearApplyRepository.findFirstByClrAppyEvtIdAndTenantIdAndIsDeletedFalse(clrAppyEvtId, tenantId)
                .ifPresent(row -> {
                    row.setStatus(YbClearApply.STATUS_REVOKED);
                    row.setClrStas("00");
                    clearApplyRepository.save(row);
                });
    }

    /**
     * 3201 日对账成功后落本地日账流水。
     *
     * @param contractId     契约主键
     * @param contractBusNo  契约业务编号
     * @param request        3201 请求参数
     * @param stmtBegndate   对账开始日期
     * @param stmtEnddate    对账结束日期
     * @param stmtRslt       对账结果码
     * @param stmtRsltDscr   对账结果说明
     * @return 持久化后的日对账实体
     */
    @Transactional
    public YbReconDaily saveReconDaily(
            Long contractId,
            String contractBusNo,
            ReconDaily3201Command request,
            LocalDate stmtBegndate,
            LocalDate stmtEnddate,
            String stmtRslt,
            String stmtRsltDscr) {
        YbReconDaily row = new YbReconDaily();
        row.setContractId(contractId);
        row.setContractBusNo(contractBusNo);
        row.setInsutype(request.getInsutype());
        row.setClrType(request.getClrType());
        row.setSetlOptins(request.getSetlOptins());
        row.setStmtBegndate(stmtBegndate);
        row.setStmtEnddate(stmtEnddate);
        row.setMedfeeSumamt(request.getMedfeeSumamt());
        row.setFundPaySumamt(request.getFundPaySumamt());
        row.setAcctPay(request.getAcctPay());
        row.setFixmedinsSetlCnt(request.getFixmedinsSetlCnt());
        row.setStmtRslt(stmtRslt);
        row.setStmtRsltDscr(stmtRsltDscr);
        return reconDailyRepository.saveAndFlush(row);
    }

    /**
     * 3202 明细对账完成后落本地明细对账流水。
     *
     * @param contractId        契约主键
     * @param contractBusNo     契约业务编号
     * @param setlOptins        结算经办机构
     * @param clrType           清算类别
     * @param refdSetlFlag      退费结算标志
     * @param stmtBegndate      对账开始日期
     * @param stmtEnddate       对账结束日期
     * @param uploadFileQuryNo  9101 上传文件查询号
     * @param uploadFilename    上传文件名
     * @param resultFileQuryNo  9102 结果文件查询号
     * @param resultFilename    结果文件名
     * @param dldEndtime        下载截止时间
     * @param rowCount          对账总行数
     * @param mismatchCount     不平行数
     * @param resultJson        9102 解析结果 JSON
     * @return 持久化后的明细对账实体
     */
    @Transactional
    public YbReconDetail saveReconDetail(
            Long contractId,
            String contractBusNo,
            String setlOptins,
            String clrType,
            String refdSetlFlag,
            LocalDate stmtBegndate,
            LocalDate stmtEnddate,
            String uploadFileQuryNo,
            String uploadFilename,
            String resultFileQuryNo,
            String resultFilename,
            String dldEndtime,
            int rowCount,
            int mismatchCount,
            String resultJson) {
        YbReconDetail detailRow = new YbReconDetail();
        detailRow.setContractId(contractId);
        detailRow.setContractBusNo(contractBusNo);
        detailRow.setSetlOptins(setlOptins);
        detailRow.setClrType(clrType);
        detailRow.setRefdSetlFlag(refdSetlFlag);
        detailRow.setStmtBegndate(stmtBegndate);
        detailRow.setStmtEnddate(stmtEnddate);
        detailRow.setUploadFileQuryNo(uploadFileQuryNo);
        detailRow.setUploadFilename(uploadFilename);
        detailRow.setResultFileQuryNo(resultFileQuryNo);
        detailRow.setResultFilename(resultFilename);
        detailRow.setDldEndtime(dldEndtime);
        detailRow.setRowCount(rowCount);
        detailRow.setMismatchCount(mismatchCount);
        detailRow.setResultJson(resultJson);
        return reconDetailRepository.saveAndFlush(detailRow);
    }

    /**
     * 查询本地明细对账流水（可按 yyyyMM 月份过滤）。
     *
     * @param request  契约与可选月份
     * @param tenantId 租户
     * @return 明细对账 DTO 列表
     */
    public List<LocalReconDetailResult> listLocalReconDetailRecords(ListLocalReconRecordsCommand request, Long tenantId) {
        List<YbReconDetail> rows = reconDetailRepository
                .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        request.getContractBusNo(), tenantId);
        List<LocalReconDetailResult> result = new ArrayList<>();
        for (YbReconDetail row : rows) {
            if (isNotEmpty(request.getMonthFilter()) && row.getStmtBegndate() != null) {
                String ym = row.getStmtBegndate().format(MONTH_FMT);
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalReconDetailDto(row));
        }
        return result;
    }

    /**
     * 查询本地清算申请流水（可按 yyyyMM 月份过滤）。
     *
     * @param request  契约与可选月份
     * @param tenantId 租户
     * @return 清算申请 DTO 列表
     */
    public List<LocalClearApplyResult> listLocalClearApplies(ListLocalReconRecordsCommand request, Long tenantId) {
        List<YbClearApply> rows = clearApplyRepository
                .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        request.getContractBusNo(), tenantId);
        List<LocalClearApplyResult> result = new ArrayList<>();
        for (YbClearApply row : rows) {
            if (isNotEmpty(request.getMonthFilter()) && row.getBegndate() != null) {
                String ym = row.getBegndate().format(MONTH_FMT);
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalClearApplyDto(row));
        }
        return result;
    }

    /**
     * 查询本地日对账流水（可按 yyyyMM 月份过滤）。
     *
     * @param request  契约与可选月份
     * @param tenantId 租户
     * @return 日对账 DTO 列表
     */
    public List<LocalReconDailyResult> listLocalReconDailyRecords(ListLocalReconRecordsCommand request, Long tenantId) {
        List<YbReconDaily> rows = reconDailyRepository
                .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        request.getContractBusNo(), tenantId);
        List<LocalReconDailyResult> result = new ArrayList<>();
        for (YbReconDaily row : rows) {
            if (isNotEmpty(request.getMonthFilter()) && row.getStmtBegndate() != null) {
                String ym = row.getStmtBegndate().format(MONTH_FMT);
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalReconDailyDto(row));
        }
        return result;
    }

    /** 明细对账实体转本地列表 DTO */
    private LocalReconDetailResult toLocalReconDetailDto(YbReconDetail row) {
        LocalReconDetailResult dto = new LocalReconDetailResult();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setSetlOptins(row.getSetlOptins());
        dto.setClrType(row.getClrType());
        dto.setStmtBegndate(formatDateOrNull(row.getStmtBegndate()));
        dto.setStmtEnddate(formatDateOrNull(row.getStmtEnddate()));
        dto.setRowCount(row.getRowCount());
        dto.setMismatchCount(row.getMismatchCount());
        dto.setUploadFileQuryNo(row.getUploadFileQuryNo());
        dto.setResultFileQuryNo(row.getResultFileQuryNo());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    /** 清算申请实体转本地列表 DTO */
    private LocalClearApplyResult toLocalClearApplyDto(YbClearApply row) {
        LocalClearApplyResult dto = new LocalClearApplyResult();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setClrType(row.getClrType());
        dto.setClrOptins(row.getClrOptins());
        dto.setBegndate(formatDateOrNull(row.getBegndate()));
        dto.setEnddate(formatDateOrNull(row.getEnddate()));
        dto.setMedfeeSumamt(row.getMedfeeSumamt());
        dto.setFundAppySum(row.getFundAppySum());
        dto.setAcctPay(row.getAcctPay());
        dto.setCashPayamt(row.getCashPayamt());
        dto.setClrAppyEvtId(row.getClrAppyEvtId());
        dto.setClrStas(row.getClrStas());
        dto.setStatus(row.getStatus());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    /** 日对账实体转本地列表 DTO */
    private LocalReconDailyResult toLocalReconDailyDto(YbReconDaily row) {
        LocalReconDailyResult dto = new LocalReconDailyResult();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setInsutype(row.getInsutype());
        dto.setClrType(row.getClrType());
        dto.setSetlOptins(row.getSetlOptins());
        dto.setStmtBegndate(formatDateOrNull(row.getStmtBegndate()));
        dto.setStmtEnddate(formatDateOrNull(row.getStmtEnddate()));
        dto.setMedfeeSumamt(row.getMedfeeSumamt());
        dto.setFundPaySumamt(row.getFundPaySumamt());
        dto.setAcctPay(row.getAcctPay());
        dto.setFixmedinsSetlCnt(row.getFixmedinsSetlCnt());
        dto.setStmtRslt(row.getStmtRslt());
        dto.setStmtRsltDscr(row.getStmtRsltDscr());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    /** {@link LocalDate} 格式化为 yyyy-MM-dd，null 返回 null */
    private static String formatDateOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FMT);
    }

    /** {@link LocalDateTime} 转 ISO 字符串，null 返回 null */
    private static String formatDateTimeOrNull(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toString();
    }
}
