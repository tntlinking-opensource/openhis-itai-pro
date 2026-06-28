/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.borrow.service;

import com.openhis.health.application.mrms.borrow.dto.BorrowRequestItemDto;
import com.openhis.health.application.mrms.borrow.mapper.BorrowRequestMapper;
import com.openhis.health.application.mrms.borrow.request.ApplyBorrowRequest;
import com.openhis.health.application.mrms.borrow.request.BorrowActionRequest;
import com.openhis.health.application.mrms.borrow.request.BorrowLifecycleRequest;
import com.openhis.health.application.mrms.borrow.request.QueryBorrowPageRequest;
import com.openhis.health.domain.mrms.borrowrequest.entity.BorrowRequest;
import com.openhis.health.domain.mrms.borrowrequest.queries.BorrowRequestPageQuery;
import com.openhis.health.domain.mrms.borrowrequest.service.BorrowRequestDomainService;
import com.openhis.health.domain.mrms.borrowrequest.service.BorrowRequestQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.BorrowRequestType;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.module.common.validator.MrmsOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 借阅复印应用服务（IF-MR-Borrow）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordBorrow/*}。负责借阅/复印申请、审批、
 * 借出/归还及分页查询；仅已归档病案可申请。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordBorrowAppService {

    private final BorrowRequestDomainService borrowRequestDomainService;
    private final BorrowRequestQueryService borrowRequestQueryService;
    private final CaseRecordDomainService caseRecordDomainService;
    private final BorrowRequestMapper borrowRequestMapper;
    private final MrmsOperatorResolver operatorResolver;
    private final PageWindowGate pageWindowGate;

    /**
     * 提交借阅/复印申请。
     *
     * @param request 申请请求
     * @return 申请行 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequestItemDto apply(ApplyBorrowRequest request) {
        // Gate：病案须已归档且用途必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        // 非归档病案禁止借阅
        if (record.getStatusEnum() != CaseRecordStatus.ARCHIVED) {
            throw new BusinessException(MrmsErrorCodes.BORROW_INVALID_STATUS, "仅已归档病案可申请借阅");
        }
        // 借阅用途必填
        if (trimToNull(request.getPurpose()) == null) {
            throw new ValidationException("借阅用途不能为空");
        }
        String typeCode = trimToNull(request.getRequestTypeCode());
        // 未传类型默认查阅
        if (typeCode == null) {
            typeCode = BorrowRequestType.BORROW.getCode();
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：领域创建借阅申请
        BorrowRequest entity = borrowRequestDomainService.apply(
                operatorResolver.requireTenantId(),
                caseRecordId,
                typeCode,
                operatorId,
                trimToNull(request.getApplicantName()),
                trimToNull(request.getApplicantDeptName()),
                request.getPurpose(),
                trimToNull(request.getScopeDesc()),
                request.getCopyCount(),
                trimToNull(request.getPatientAuthorizationInfo()),
                operatorId);
        // Asm：映射出参
        return borrowRequestMapper.toItemDto(entity, record);
    }

    /**
     * 审批通过借阅申请。
     *
     * @param request 审批请求
     * @return 申请行 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequestItemDto approve(BorrowActionRequest request) {
        // Gate：借阅主键必填
        BorrowRequest entity = requireBorrowAndRecord(request.getBorrowRequestId());
        // Cmd：领域审批
        entity = borrowRequestDomainService.approve(
                request.getBorrowRequestId(), request.getComment(), operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.requireById(entity.getCaseRecordId());
        // Asm：映射出参
        return borrowRequestMapper.toItemDto(entity, record);
    }

    /**
     * 驳回借阅申请。
     *
     * @param request 驳回请求
     * @return 申请行 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequestItemDto reject(BorrowActionRequest request) {
        // Gate：借阅主键必填
        BorrowRequest entity = requireBorrowAndRecord(request.getBorrowRequestId());
        // Cmd：领域驳回
        entity = borrowRequestDomainService.reject(
                request.getBorrowRequestId(), request.getComment(), operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.requireById(entity.getCaseRecordId());
        // Asm：映射出参
        return borrowRequestMapper.toItemDto(entity, record);
    }

    /**
     * 标记已借出。
     *
     * @param request 借出请求
     * @return 申请行 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequestItemDto markBorrowed(BorrowLifecycleRequest request) {
        // Gate：借阅主键必填
        requireBorrowId(request.getBorrowRequestId());
        // Cmd：领域登记借出时间与应还日期
        BorrowRequest entity = borrowRequestDomainService.markBorrowed(
                request.getBorrowRequestId(),
                request.getDueReturnAt(),
                operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.requireById(entity.getCaseRecordId());
        // Asm：映射出参
        return borrowRequestMapper.toItemDto(entity, record);
    }

    /**
     * 标记已归还。
     *
     * @param request 归还请求
     * @return 申请行 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequestItemDto markReturned(BorrowLifecycleRequest request) {
        // Gate：借阅主键必填
        requireBorrowId(request.getBorrowRequestId());
        // Cmd：领域登记归还
        BorrowRequest entity = borrowRequestDomainService.markReturned(
                request.getBorrowRequestId(), operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.requireById(entity.getCaseRecordId());
        // Asm：映射出参
        return borrowRequestMapper.toItemDto(entity, record);
    }

    /**
     * 分页检索借阅申请。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public PagedResult<BorrowRequestItemDto> queryPage(QueryBorrowPageRequest request) {
        // Gate：分页窗口合法
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        // 分页上限 200，防止大页拖垮列表
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：组装领域分页查询
        BorrowRequestPageQuery query = new BorrowRequestPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setStatusCode(trimToNull(request.getStatusCode()));
        query.setCaseRecordId(request.getCaseRecordId());
        Page<BorrowRequest> page = borrowRequestQueryService.queryPage(
                query,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, CaseRecord> recordMap = loadCaseRecords(page.getContent());
        // Asm：批量映射列表行（附带病案号/姓名）
        List<BorrowRequestItemDto> items = new ArrayList<>();
        for (BorrowRequest row : page.getContent()) {
            items.add(borrowRequestMapper.toItemDto(row, recordMap.get(row.getCaseRecordId())));
        }
        return PagedResult.of(items, page.getTotalElements(), pageNo, pageSize);
    }

    /**
     * 批量加载列表行关联的病案主档，避免 N+1。
     *
     * @param rows 借阅申请分页行
     * @return 病案主键 → 主档
     */
    private Map<Long, CaseRecord> loadCaseRecords(List<BorrowRequest> rows) {
        Map<Long, CaseRecord> map = new HashMap<>();
        for (BorrowRequest row : rows) {
            // 同一页内同一病案只加载一次主档
            if (row.getCaseRecordId() == null || map.containsKey(row.getCaseRecordId())) {
                continue;
            }
            map.put(row.getCaseRecordId(), caseRecordDomainService.requireById(row.getCaseRecordId()));
        }
        return map;
    }

    /**
     * 加载借阅申请并校验主键（审批/驳回用）。
     *
     * @param borrowRequestId 借阅申请主键
     * @return 借阅实体
     */
    private BorrowRequest requireBorrowAndRecord(Long borrowRequestId) {
        requireBorrowId(borrowRequestId);
        return borrowRequestDomainService.requireById(borrowRequestId);
    }

    /**
     * 校验借阅申请主键非空。
     *
     * @param borrowRequestId 借阅申请主键
     */
    private void requireBorrowId(Long borrowRequestId) {
        if (borrowRequestId == null) {
            throw new ValidationException("借阅申请主键不能为空");
        }
    }

    /**
     * 校验病案主键非空。
     *
     * @param caseRecordId 病案主档主键
     * @return 非 null 主键
     */
    private Long requireCaseRecordId(Long caseRecordId) {
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        return caseRecordId;
    }
}
