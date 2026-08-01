/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.dict;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.crb.domain.dict.CrbCodeMapDomainService;
import com.openhis.whale.crb.domain.dict.CrbDeptCodeMap;
import com.openhis.whale.crb.domain.dict.CrbDrugCodeMap;
import com.openhis.whale.crb.domain.dict.CrbLabItemMap;
import com.openhis.whale.crb.domain.dict.command.ImportDeptCodeMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportDrugCodeMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportLabItemMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportResult;
import com.openhis.whale.crb.domain.dict.command.SoftDeleteMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertDeptCodeMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertDrugCodeMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertLabItemMapCommand;
import com.openhis.whale.crb.domain.dict.queries.CrbCodeMapQueryService;
import com.openhis.whale.crb.domain.dict.queries.DeptCodeMapPageCriteria;
import com.openhis.whale.crb.domain.dict.queries.DrugCodeMapPageCriteria;
import com.openhis.whale.crb.domain.dict.queries.LabItemMapPageCriteria;
import com.openhis.whale.crb.domain.dict.queries.model.DeptCodeMapRowModel;
import com.openhis.whale.crb.domain.dict.queries.model.DrugCodeMapRowModel;
import com.openhis.whale.crb.domain.dict.queries.model.LabItemMapRowModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.dict.CrbDictConstants.DEFAULT_PAGE_SIZE;
import static com.openhis.whale.crb.domain.dict.CrbDictConstants.MAX_IMPORT_ROWS;
import static com.openhis.whale.crb.domain.dict.CrbDictConstants.MAX_PAGE_SIZE;

/**
 * 编码对照维护 AppService（POST /api/app/infectiousDict/{methodName}）。
 *
 * <p>实现 AS-CRB-DICT IF-01～09；IF-10 pushAllBaseDept 本轮跳过（application 不可注入 bridge Orchestrator）。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousDictAppService {

    private final CrbCodeMapDomainService codeMapDomainService;
    private final CrbCodeMapQueryService codeMapQueryService;

    /**
     * IF-01 科室对照分页查询。
     *
     * @param tenantId 租户 id
     * @param request 分页条件
     * @return 分页结果
     */
    public PagedResult<DeptCodeMapRowDto> pageDeptCodeMaps(Long tenantId, PageDeptCodeMapRequest request) {
        // Gate：租户必填
        if (tenantId == null) {
            return emptyPage(request);
        }
        PageDeptCodeMapRequest safeRequest = normalizePageRequest(request);
        // Pre：组装查询条件
        DeptCodeMapPageCriteria criteria = new DeptCodeMapPageCriteria();
        criteria.setTenantId(tenantId);
        criteria.setKeyword(safeRequest.getKeyword());
        criteria.setUnmappedOnly(safeRequest.getUnmappedOnly());
        PageRequest pageable = buildPageable(safeRequest.getPageNo(), safeRequest.getPageSize());
        // Query：领域读侧分页
        Page<DeptCodeMapRowModel> page = codeMapQueryService.pageDeptCodeMaps(criteria, pageable);
        // Asm：映射 DTO
        return toDeptPageResult(page, safeRequest.getPageNo(), safeRequest.getPageSize());
    }

    /**
     * IF-02 保存科室对照。
     *
     * @param tenantId 租户 id
     * @param request 保存请求
     * @return 保存结果
     */
    public SaveDeptCodeMapResponse saveDeptCodeMap(Long tenantId, SaveDeptCodeMapRequest request) {
        // Gate：租户与业务码必填
        if (tenantId == null || request == null || trimToNull(request.getLocationBusNo()) == null) {
            throw new IllegalArgumentException("tenantId 与 locationBusNo 不能为空");
        }
        // Pre：组装 upsert 命令（成对校验在领域）
        UpsertDeptCodeMapCommand command = new UpsertDeptCodeMapCommand();
        command.setTenantId(tenantId);
        command.setId(request.getId());
        command.setLocationBusNo(request.getLocationBusNo());
        command.setLocationName(request.getLocationName());
        command.setTargetDeptCode(request.getTargetDeptCode());
        command.setTargetDeptName(request.getTargetDeptName());
        // Cmd：领域 upsert
        CrbDeptCodeMap saved = codeMapDomainService.upsertDeptCodeMap(command);
        // Asm：返回 id 与 mapped
        SaveDeptCodeMapResponse response = new SaveDeptCodeMapResponse();
        response.setId(saved.getId());
        response.setMapped(saved.isMapped());
        return response;
    }

    /**
     * IF-03 导入科室对照。
     *
     * @param tenantId 租户 id
     * @param request 导入请求
     * @return 导入汇总
     */
    public ImportMapResponse importDeptCodeMaps(Long tenantId, ImportDeptCodeMapRequest request) {
        // Gate：租户与行列表必填
        if (tenantId == null || request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("导入行不能为空");
        }
        // Gate：行数上限
        if (request.getRows().size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("单次导入不得超过 " + MAX_IMPORT_ROWS + " 行");
        }
        // Pre：组装导入命令
        ImportDeptCodeMapsCommand command = new ImportDeptCodeMapsCommand();
        command.setTenantId(tenantId);
        command.setRows(toDeptUpsertCommands(tenantId, request.getRows()));
        // Cmd：领域批量导入
        ImportResult result = codeMapDomainService.importDeptCodeMaps(command);
        // Asm：汇总响应
        return toImportResponse(result);
    }

    /**
     * 科室对照软删。
     *
     * @param tenantId 租户 id
     * @param id 对照主键
     */
    public void softDeleteDeptCodeMap(Long tenantId, Long id) {
        // Gate：租户与主键必填
        if (tenantId == null || id == null) {
            throw new IllegalArgumentException("tenantId 与 id 不能为空");
        }
        SoftDeleteMapCommand command = new SoftDeleteMapCommand();
        command.setTenantId(tenantId);
        command.setId(id);
        // Cmd：领域软删
        codeMapDomainService.softDeleteDeptCodeMap(command);
    }

    /**
     * IF-04 检验对照分页查询。
     *
     * @param tenantId 租户 id
     * @param request 分页条件
     * @return 分页结果
     */
    public PagedResult<LabItemMapRowDto> pageLabItemMaps(Long tenantId, PageLabItemMapRequest request) {
        // Gate：租户必填
        if (tenantId == null) {
            return emptyLabPage(request);
        }
        PageLabItemMapRequest safeRequest = normalizeLabPageRequest(request);
        LabItemMapPageCriteria criteria = new LabItemMapPageCriteria();
        criteria.setTenantId(tenantId);
        criteria.setKeyword(safeRequest.getKeyword());
        criteria.setUnmappedOnly(safeRequest.getUnmappedOnly());
        PageRequest pageable = buildPageable(safeRequest.getPageNo(), safeRequest.getPageSize());
        Page<LabItemMapRowModel> page = codeMapQueryService.pageLabItemMaps(criteria, pageable);
        return toLabPageResult(page, safeRequest.getPageNo(), safeRequest.getPageSize());
    }

    /**
     * IF-05 保存检验对照。
     *
     * @param tenantId 租户 id
     * @param request 保存请求
     * @return 保存结果
     */
    public SaveLabItemMapResponse saveLabItemMap(Long tenantId, SaveLabItemMapRequest request) {
        // Gate：租户与项目业务码必填
        if (tenantId == null || request == null || trimToNull(request.getItemBusNo()) == null) {
            throw new IllegalArgumentException("tenantId 与 itemBusNo 不能为空");
        }
        UpsertLabItemMapCommand command = new UpsertLabItemMapCommand();
        command.setTenantId(tenantId);
        command.setId(request.getId());
        command.setItemBusNo(request.getItemBusNo());
        command.setItemName(request.getItemName());
        command.setNationalItemCode(request.getNationalItemCode());
        command.setResultShape(request.getResultShape());
        command.setAnnex2Scope(request.getAnnex2Scope());
        command.setPushEnabled(request.getPushEnabled());
        // Cmd：领域 upsert（范围外可能强制关闭推送）
        CrbLabItemMap saved = codeMapDomainService.upsertLabItemMap(command);
        // Asm：返回 id / mapped / 实际 pushEnabled；若被强制关闭则附提示
        SaveLabItemMapResponse response = new SaveLabItemMapResponse();
        response.setId(saved.getId());
        response.setMapped(saved.isMapped());
        response.setPushEnabled(saved.getPushEnabled());
        boolean requestedPush = true;
        if (request.getPushEnabled() != null) {
            requestedPush = request.getPushEnabled();
        }
        boolean actualPush = Boolean.TRUE.equals(saved.getPushEnabled());
        // 请求启用但领域按附件二规则关闭时，提示信息科
        if (requestedPush && !actualPush) {
            response.setPushForcedOff(true);
            response.setHint("附件二范围外已强制关闭推送");
        }
        return response;
    }

    /**
     * IF-06 导入检验对照。
     *
     * @param tenantId 租户 id
     * @param request 导入请求
     * @return 导入汇总
     */
    public ImportMapResponse importLabItemMaps(Long tenantId, ImportLabItemMapRequest request) {
        if (tenantId == null || request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("导入行不能为空");
        }
        if (request.getRows().size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("单次导入不得超过 " + MAX_IMPORT_ROWS + " 行");
        }
        ImportLabItemMapsCommand command = new ImportLabItemMapsCommand();
        command.setTenantId(tenantId);
        command.setRows(toLabUpsertCommands(tenantId, request.getRows()));
        ImportResult result = codeMapDomainService.importLabItemMaps(command);
        return toImportResponse(result);
    }

    /**
     * 检验对照软删。
     *
     * @param tenantId 租户 id
     * @param id 对照主键
     */
    public void softDeleteLabItemMap(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new IllegalArgumentException("tenantId 与 id 不能为空");
        }
        SoftDeleteMapCommand command = new SoftDeleteMapCommand();
        command.setTenantId(tenantId);
        command.setId(id);
        codeMapDomainService.softDeleteLabItemMap(command);
    }

    /**
     * IF-07 药品对照分页查询。
     *
     * @param tenantId 租户 id
     * @param request 分页条件
     * @return 分页结果
     */
    public PagedResult<DrugCodeMapRowDto> pageDrugCodeMaps(Long tenantId, PageDrugCodeMapRequest request) {
        if (tenantId == null) {
            return emptyDrugPage(request);
        }
        PageDrugCodeMapRequest safeRequest = normalizeDrugPageRequest(request);
        DrugCodeMapPageCriteria criteria = new DrugCodeMapPageCriteria();
        criteria.setTenantId(tenantId);
        criteria.setKeyword(safeRequest.getKeyword());
        criteria.setUnmappedOnly(safeRequest.getUnmappedOnly());
        PageRequest pageable = buildPageable(safeRequest.getPageNo(), safeRequest.getPageSize());
        Page<DrugCodeMapRowModel> page = codeMapQueryService.pageDrugCodeMaps(criteria, pageable);
        return toDrugPageResult(page, safeRequest.getPageNo(), safeRequest.getPageSize());
    }

    /**
     * IF-08 保存药品对照。
     *
     * @param tenantId 租户 id
     * @param request 保存请求
     * @return 保存结果
     */
    public SaveDrugCodeMapResponse saveDrugCodeMap(Long tenantId, SaveDrugCodeMapRequest request) {
        if (tenantId == null || request == null || trimToNull(request.getMedBusNo()) == null) {
            throw new IllegalArgumentException("tenantId 与 medBusNo 不能为空");
        }
        UpsertDrugCodeMapCommand command = new UpsertDrugCodeMapCommand();
        command.setTenantId(tenantId);
        command.setId(request.getId());
        command.setMedBusNo(request.getMedBusNo());
        command.setMedName(request.getMedName());
        command.setNationalDrugCode(request.getNationalDrugCode());
        command.setNationalDrugName(request.getNationalDrugName());
        CrbDrugCodeMap saved = codeMapDomainService.upsertDrugCodeMap(command);
        SaveDrugCodeMapResponse response = new SaveDrugCodeMapResponse();
        response.setId(saved.getId());
        response.setMapped(saved.isMapped());
        return response;
    }

    /**
     * IF-09 导入药品对照。
     *
     * @param tenantId 租户 id
     * @param request 导入请求
     * @return 导入汇总
     */
    public ImportMapResponse importDrugCodeMaps(Long tenantId, ImportDrugCodeMapRequest request) {
        if (tenantId == null || request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("导入行不能为空");
        }
        if (request.getRows().size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("单次导入不得超过 " + MAX_IMPORT_ROWS + " 行");
        }
        ImportDrugCodeMapsCommand command = new ImportDrugCodeMapsCommand();
        command.setTenantId(tenantId);
        command.setRows(toDrugUpsertCommands(tenantId, request.getRows()));
        ImportResult result = codeMapDomainService.importDrugCodeMaps(command);
        return toImportResponse(result);
    }

    /**
     * 药品对照软删。
     *
     * @param tenantId 租户 id
     * @param id 对照主键
     */
    public void softDeleteDrugCodeMap(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new IllegalArgumentException("tenantId 与 id 不能为空");
        }
        SoftDeleteMapCommand command = new SoftDeleteMapCommand();
        command.setTenantId(tenantId);
        command.setId(id);
        codeMapDomainService.softDeleteDrugCodeMap(command);
    }

    /**
     * 构建 Spring Data 分页（updatedAt DESC）。
     *
     * @param pageNo 页码
     * @param pageSize 条数
     * @return PageRequest
     */
    private PageRequest buildPageable(int pageNo, int pageSize) {
        int safePageNo = pageNo;
        if (safePageNo < 1) {
            safePageNo = 1;
        }
        int safePageSize = pageSize;
        if (safePageSize < 1) {
            safePageSize = DEFAULT_PAGE_SIZE;
        }
        if (safePageSize > MAX_PAGE_SIZE) {
            safePageSize = MAX_PAGE_SIZE;
        }
        return PageRequest.of(
                safePageNo - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));
    }

    /**
     * 归一化科室分页请求。
     *
     * @param request 原始请求
     * @return 安全请求
     */
    private PageDeptCodeMapRequest normalizePageRequest(PageDeptCodeMapRequest request) {
        PageDeptCodeMapRequest safe = request;
        if (safe == null) {
            safe = new PageDeptCodeMapRequest();
        }
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(DEFAULT_PAGE_SIZE);
        }
        if (safe.getPageSize() > MAX_PAGE_SIZE) {
            safe.setPageSize(MAX_PAGE_SIZE);
        }
        return safe;
    }

    /**
     * 归一化检验分页请求。
     *
     * @param request 原始请求
     * @return 安全请求
     */
    private PageLabItemMapRequest normalizeLabPageRequest(PageLabItemMapRequest request) {
        PageLabItemMapRequest safe = request;
        if (safe == null) {
            safe = new PageLabItemMapRequest();
        }
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(DEFAULT_PAGE_SIZE);
        }
        if (safe.getPageSize() > MAX_PAGE_SIZE) {
            safe.setPageSize(MAX_PAGE_SIZE);
        }
        return safe;
    }

    /**
     * 归一化药品分页请求。
     *
     * @param request 原始请求
     * @return 安全请求
     */
    private PageDrugCodeMapRequest normalizeDrugPageRequest(PageDrugCodeMapRequest request) {
        PageDrugCodeMapRequest safe = request;
        if (safe == null) {
            safe = new PageDrugCodeMapRequest();
        }
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(DEFAULT_PAGE_SIZE);
        }
        if (safe.getPageSize() > MAX_PAGE_SIZE) {
            safe.setPageSize(MAX_PAGE_SIZE);
        }
        return safe;
    }

    /**
     * 科室导入行转 upsert 命令列表。
     *
     * @param tenantId 租户 id
     * @param rows 导入行
     * @return 命令列表
     */
    private List<UpsertDeptCodeMapCommand> toDeptUpsertCommands(
            Long tenantId,
            List<SaveDeptCodeMapRequest> rows) {
        List<UpsertDeptCodeMapCommand> commands = new ArrayList<>();
        for (SaveDeptCodeMapRequest row : rows) {
            UpsertDeptCodeMapCommand command = new UpsertDeptCodeMapCommand();
            command.setTenantId(tenantId);
            if (row != null) {
                command.setId(row.getId());
                command.setLocationBusNo(row.getLocationBusNo());
                command.setLocationName(row.getLocationName());
                command.setTargetDeptCode(row.getTargetDeptCode());
                command.setTargetDeptName(row.getTargetDeptName());
            }
            commands.add(command);
        }
        return commands;
    }

    /**
     * 检验导入行转 upsert 命令列表。
     *
     * @param tenantId 租户 id
     * @param rows 导入行
     * @return 命令列表
     */
    private List<UpsertLabItemMapCommand> toLabUpsertCommands(
            Long tenantId,
            List<SaveLabItemMapRequest> rows) {
        List<UpsertLabItemMapCommand> commands = new ArrayList<>();
        for (SaveLabItemMapRequest row : rows) {
            UpsertLabItemMapCommand command = new UpsertLabItemMapCommand();
            command.setTenantId(tenantId);
            if (row != null) {
                command.setId(row.getId());
                command.setItemBusNo(row.getItemBusNo());
                command.setItemName(row.getItemName());
                command.setNationalItemCode(row.getNationalItemCode());
                command.setResultShape(row.getResultShape());
                command.setAnnex2Scope(row.getAnnex2Scope());
                command.setPushEnabled(row.getPushEnabled());
            }
            commands.add(command);
        }
        return commands;
    }

    /**
     * 药品导入行转 upsert 命令列表。
     *
     * @param tenantId 租户 id
     * @param rows 导入行
     * @return 命令列表
     */
    private List<UpsertDrugCodeMapCommand> toDrugUpsertCommands(
            Long tenantId,
            List<SaveDrugCodeMapRequest> rows) {
        List<UpsertDrugCodeMapCommand> commands = new ArrayList<>();
        for (SaveDrugCodeMapRequest row : rows) {
            UpsertDrugCodeMapCommand command = new UpsertDrugCodeMapCommand();
            command.setTenantId(tenantId);
            if (row != null) {
                command.setId(row.getId());
                command.setMedBusNo(row.getMedBusNo());
                command.setMedName(row.getMedName());
                command.setNationalDrugCode(row.getNationalDrugCode());
                command.setNationalDrugName(row.getNationalDrugName());
            }
            commands.add(command);
        }
        return commands;
    }

    /**
     * 领域导入结果转响应 DTO。
     *
     * @param result 导入结果
     * @return 响应
     */
    private ImportMapResponse toImportResponse(ImportResult result) {
        ImportMapResponse response = new ImportMapResponse();
        response.setSuccessCount(result.getSuccessCount());
        response.setSkipCount(result.getSkipCount());
        response.setFailCount(result.getFailCount());
        response.setMessages(result.getMessages());
        return response;
    }

    /**
     * 科室分页模型转 PagedResult。
     *
     * @param page 领域分页
     * @param pageNo 页码
     * @param pageSize 条数
     * @return PagedResult
     */
    private PagedResult<DeptCodeMapRowDto> toDeptPageResult(
            Page<DeptCodeMapRowModel> page,
            int pageNo,
            int pageSize) {
        List<DeptCodeMapRowDto> content = page.getContent().stream()
                .map(this::toDeptRowDto)
                .toList();
        PagedResult<DeptCodeMapRowDto> result = new PagedResult<>();
        result.setContent(content);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 检验分页模型转 PagedResult。
     *
     * @param page 领域分页
     * @param pageNo 页码
     * @param pageSize 条数
     * @return PagedResult
     */
    private PagedResult<LabItemMapRowDto> toLabPageResult(
            Page<LabItemMapRowModel> page,
            int pageNo,
            int pageSize) {
        List<LabItemMapRowDto> content = page.getContent().stream()
                .map(this::toLabRowDto)
                .toList();
        PagedResult<LabItemMapRowDto> result = new PagedResult<>();
        result.setContent(content);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 药品分页模型转 PagedResult。
     *
     * @param page 领域分页
     * @param pageNo 页码
     * @param pageSize 条数
     * @return PagedResult
     */
    private PagedResult<DrugCodeMapRowDto> toDrugPageResult(
            Page<DrugCodeMapRowModel> page,
            int pageNo,
            int pageSize) {
        List<DrugCodeMapRowDto> content = page.getContent().stream()
                .map(this::toDrugRowDto)
                .toList();
        PagedResult<DrugCodeMapRowDto> result = new PagedResult<>();
        result.setContent(content);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 科室行模型转 DTO。
     *
     * @param model 行模型
     * @return DTO
     */
    private DeptCodeMapRowDto toDeptRowDto(DeptCodeMapRowModel model) {
        DeptCodeMapRowDto dto = new DeptCodeMapRowDto();
        dto.setId(model.getId());
        dto.setLocationBusNo(model.getLocationBusNo());
        dto.setLocationName(model.getLocationName());
        dto.setTargetDeptCode(model.getTargetDeptCode());
        dto.setTargetDeptName(model.getTargetDeptName());
        dto.setMapped(model.getMapped());
        return dto;
    }

    /**
     * 检验行模型转 DTO。
     *
     * @param model 行模型
     * @return DTO
     */
    private LabItemMapRowDto toLabRowDto(LabItemMapRowModel model) {
        LabItemMapRowDto dto = new LabItemMapRowDto();
        dto.setId(model.getId());
        dto.setItemBusNo(model.getItemBusNo());
        dto.setItemName(model.getItemName());
        dto.setNationalItemCode(model.getNationalItemCode());
        dto.setResultShape(model.getResultShape());
        dto.setAnnex2Scope(model.getAnnex2Scope());
        dto.setPushEnabled(model.getPushEnabled());
        dto.setMapped(model.getMapped());
        return dto;
    }

    /**
     * 药品行模型转 DTO。
     *
     * @param model 行模型
     * @return DTO
     */
    private DrugCodeMapRowDto toDrugRowDto(DrugCodeMapRowModel model) {
        DrugCodeMapRowDto dto = new DrugCodeMapRowDto();
        dto.setId(model.getId());
        dto.setMedBusNo(model.getMedBusNo());
        dto.setMedName(model.getMedName());
        dto.setNationalDrugCode(model.getNationalDrugCode());
        dto.setNationalDrugName(model.getNationalDrugName());
        dto.setMapped(model.getMapped());
        return dto;
    }

    /**
     * 租户缺失时空科室分页。
     *
     * @param request 请求
     * @return 空分页
     */
    private PagedResult<DeptCodeMapRowDto> emptyPage(PageDeptCodeMapRequest request) {
        PageDeptCodeMapRequest safe = normalizePageRequest(request);
        PagedResult<DeptCodeMapRowDto> empty = new PagedResult<>();
        empty.setContent(List.of());
        empty.setTotalCount(0L);
        empty.setPageNo(safe.getPageNo());
        empty.setPageSize(safe.getPageSize());
        return empty;
    }

    /**
     * 租户缺失时空检验分页。
     *
     * @param request 请求
     * @return 空分页
     */
    private PagedResult<LabItemMapRowDto> emptyLabPage(PageLabItemMapRequest request) {
        PageLabItemMapRequest safe = normalizeLabPageRequest(request);
        PagedResult<LabItemMapRowDto> empty = new PagedResult<>();
        empty.setContent(List.of());
        empty.setTotalCount(0L);
        empty.setPageNo(safe.getPageNo());
        empty.setPageSize(safe.getPageSize());
        return empty;
    }

    /**
     * 租户缺失时空药品分页。
     *
     * @param request 请求
     * @return 空分页
     */
    private PagedResult<DrugCodeMapRowDto> emptyDrugPage(PageDrugCodeMapRequest request) {
        PageDrugCodeMapRequest safe = normalizeDrugPageRequest(request);
        PagedResult<DrugCodeMapRowDto> empty = new PagedResult<>();
        empty.setContent(List.of());
        empty.setTotalCount(0L);
        empty.setPageNo(safe.getPageNo());
        empty.setPageSize(safe.getPageSize());
        return empty;
    }

    /** 科室分页请求（IF-01）。 */
    public static class PageDeptCodeMapRequest {

        /** 页码，从 1 开始。 */
        private int pageNo = 1;

        /** 每页条数。 */
        private int pageSize = DEFAULT_PAGE_SIZE;

        /** 关键字。 */
        private String keyword;

        /** 仅未对照。 */
        private Boolean unmappedOnly;

        /** @return 页码 */
        public int getPageNo() { return pageNo; }
        /** @param pageNo 页码 */
        public void setPageNo(int pageNo) { this.pageNo = pageNo; }
        /** @return 每页条数 */
        public int getPageSize() { return pageSize; }
        /** @param pageSize 每页条数 */
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        /** @return 关键字 */
        public String getKeyword() { return keyword; }
        /** @param keyword 关键字 */
        public void setKeyword(String keyword) { this.keyword = keyword; }
        /** @return 仅未对照 */
        public Boolean getUnmappedOnly() { return unmappedOnly; }
        /** @param unmappedOnly 仅未对照 */
        public void setUnmappedOnly(Boolean unmappedOnly) { this.unmappedOnly = unmappedOnly; }
    }

    /** 检验分页请求（IF-04）。 */
    public static class PageLabItemMapRequest {

        /** 页码，从 1 开始。 */
        private int pageNo = 1;

        /** 每页条数。 */
        private int pageSize = DEFAULT_PAGE_SIZE;

        /** 关键字。 */
        private String keyword;

        /** 仅未对照。 */
        private Boolean unmappedOnly;

        /** @return 页码 */
        public int getPageNo() { return pageNo; }
        /** @param pageNo 页码 */
        public void setPageNo(int pageNo) { this.pageNo = pageNo; }
        /** @return 每页条数 */
        public int getPageSize() { return pageSize; }
        /** @param pageSize 每页条数 */
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        /** @return 关键字 */
        public String getKeyword() { return keyword; }
        /** @param keyword 关键字 */
        public void setKeyword(String keyword) { this.keyword = keyword; }
        /** @return 仅未对照 */
        public Boolean getUnmappedOnly() { return unmappedOnly; }
        /** @param unmappedOnly 仅未对照 */
        public void setUnmappedOnly(Boolean unmappedOnly) { this.unmappedOnly = unmappedOnly; }
    }

    /** 药品分页请求（IF-07）。 */
    public static class PageDrugCodeMapRequest {

        /** 页码，从 1 开始。 */
        private int pageNo = 1;

        /** 每页条数。 */
        private int pageSize = DEFAULT_PAGE_SIZE;

        /** 关键字。 */
        private String keyword;

        /** 仅未对照。 */
        private Boolean unmappedOnly;

        /** @return 页码 */
        public int getPageNo() { return pageNo; }
        /** @param pageNo 页码 */
        public void setPageNo(int pageNo) { this.pageNo = pageNo; }
        /** @return 每页条数 */
        public int getPageSize() { return pageSize; }
        /** @param pageSize 每页条数 */
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        /** @return 关键字 */
        public String getKeyword() { return keyword; }
        /** @param keyword 关键字 */
        public void setKeyword(String keyword) { this.keyword = keyword; }
        /** @return 仅未对照 */
        public Boolean getUnmappedOnly() { return unmappedOnly; }
        /** @param unmappedOnly 仅未对照 */
        public void setUnmappedOnly(Boolean unmappedOnly) { this.unmappedOnly = unmappedOnly; }
    }

    /** 保存科室对照请求（IF-02）。 */
    public static class SaveDeptCodeMapRequest {

        /** 主键，空则按 busNo upsert。 */
        private Long id;

        /** 院内科室业务码。 */
        private String locationBusNo;

        /** 院内科室名称。 */
        private String locationName;

        /** 国家科室码。 */
        private String targetDeptCode;

        /** 国家科室名称。 */
        private String targetDeptName;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内科室业务码 */
        public String getLocationBusNo() { return locationBusNo; }
        /** @param locationBusNo 院内科室业务码 */
        public void setLocationBusNo(String locationBusNo) { this.locationBusNo = locationBusNo; }
        /** @return 院内科室名称 */
        public String getLocationName() { return locationName; }
        /** @param locationName 院内科室名称 */
        public void setLocationName(String locationName) { this.locationName = locationName; }
        /** @return 国家科室码 */
        public String getTargetDeptCode() { return targetDeptCode; }
        /** @param targetDeptCode 国家科室码 */
        public void setTargetDeptCode(String targetDeptCode) { this.targetDeptCode = targetDeptCode; }
        /** @return 国家科室名称 */
        public String getTargetDeptName() { return targetDeptName; }
        /** @param targetDeptName 国家科室名称 */
        public void setTargetDeptName(String targetDeptName) { this.targetDeptName = targetDeptName; }
    }

    /** 保存检验对照请求（IF-05）。 */
    public static class SaveLabItemMapRequest {

        /** 主键。 */
        private Long id;

        /** 院内项目业务码。 */
        private String itemBusNo;

        /** 院内项目名称。 */
        private String itemName;

        /** 国家项目码。 */
        private String nationalItemCode;

        /** 结果形态。 */
        private String resultShape;

        /** 附件二范围。 */
        private String annex2Scope;

        /** 是否启用推送。 */
        private Boolean pushEnabled;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内项目业务码 */
        public String getItemBusNo() { return itemBusNo; }
        /** @param itemBusNo 院内项目业务码 */
        public void setItemBusNo(String itemBusNo) { this.itemBusNo = itemBusNo; }
        /** @return 院内项目名称 */
        public String getItemName() { return itemName; }
        /** @param itemName 院内项目名称 */
        public void setItemName(String itemName) { this.itemName = itemName; }
        /** @return 国家项目码 */
        public String getNationalItemCode() { return nationalItemCode; }
        /** @param nationalItemCode 国家项目码 */
        public void setNationalItemCode(String nationalItemCode) { this.nationalItemCode = nationalItemCode; }
        /** @return 结果形态 */
        public String getResultShape() { return resultShape; }
        /** @param resultShape 结果形态 */
        public void setResultShape(String resultShape) { this.resultShape = resultShape; }
        /** @return 附件二范围 */
        public String getAnnex2Scope() { return annex2Scope; }
        /** @param annex2Scope 附件二范围 */
        public void setAnnex2Scope(String annex2Scope) { this.annex2Scope = annex2Scope; }
        /** @return 是否启用推送 */
        public Boolean getPushEnabled() { return pushEnabled; }
        /** @param pushEnabled 是否启用推送 */
        public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
    }

    /** 保存药品对照请求（IF-08）。 */
    public static class SaveDrugCodeMapRequest {

        /** 主键。 */
        private Long id;

        /** 院内药品业务码。 */
        private String medBusNo;

        /** 院内药品名称。 */
        private String medName;

        /** 国家药品码。 */
        private String nationalDrugCode;

        /** 国家药品名称。 */
        private String nationalDrugName;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内药品业务码 */
        public String getMedBusNo() { return medBusNo; }
        /** @param medBusNo 院内药品业务码 */
        public void setMedBusNo(String medBusNo) { this.medBusNo = medBusNo; }
        /** @return 院内药品名称 */
        public String getMedName() { return medName; }
        /** @param medName 院内药品名称 */
        public void setMedName(String medName) { this.medName = medName; }
        /** @return 国家药品码 */
        public String getNationalDrugCode() { return nationalDrugCode; }
        /** @param nationalDrugCode 国家药品码 */
        public void setNationalDrugCode(String nationalDrugCode) { this.nationalDrugCode = nationalDrugCode; }
        /** @return 国家药品名称 */
        public String getNationalDrugName() { return nationalDrugName; }
        /** @param nationalDrugName 国家药品名称 */
        public void setNationalDrugName(String nationalDrugName) { this.nationalDrugName = nationalDrugName; }
    }

    /** 科室导入请求（IF-03）。 */
    public static class ImportDeptCodeMapRequest {

        /** 导入行。 */
        private List<SaveDeptCodeMapRequest> rows = new ArrayList<>();

        /** @return 导入行 */
        public List<SaveDeptCodeMapRequest> getRows() { return rows; }
        /** @param rows 导入行 */
        public void setRows(List<SaveDeptCodeMapRequest> rows) { this.rows = rows; }
    }

    /** 检验导入请求（IF-06）。 */
    public static class ImportLabItemMapRequest {

        /** 导入行。 */
        private List<SaveLabItemMapRequest> rows = new ArrayList<>();

        /** @return 导入行 */
        public List<SaveLabItemMapRequest> getRows() { return rows; }
        /** @param rows 导入行 */
        public void setRows(List<SaveLabItemMapRequest> rows) { this.rows = rows; }
    }

    /** 药品导入请求（IF-09）。 */
    public static class ImportDrugCodeMapRequest {

        /** 导入行。 */
        private List<SaveDrugCodeMapRequest> rows = new ArrayList<>();

        /** @return 导入行 */
        public List<SaveDrugCodeMapRequest> getRows() { return rows; }
        /** @param rows 导入行 */
        public void setRows(List<SaveDrugCodeMapRequest> rows) { this.rows = rows; }
    }

    /** 保存科室对照响应（IF-02）。 */
    public static class SaveDeptCodeMapResponse {

        /** 主键。 */
        private Long id;

        /** 是否已对照。 */
        private Boolean mapped;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
    }

    /** 保存检验对照响应（IF-05）。 */
    public static class SaveLabItemMapResponse {

        /** 主键。 */
        private Long id;

        /** 是否已对照。 */
        private Boolean mapped;

        /** 是否启用推送。 */
        private Boolean pushEnabled;

        /** 请求启用但被附件二规则强制关闭。 */
        private Boolean pushForcedOff;

        /** 给信息科的提示文案（可空）。 */
        private String hint;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
        /** @return 是否启用推送 */
        public Boolean getPushEnabled() { return pushEnabled; }
        /** @param pushEnabled 是否启用推送 */
        public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
        /** @return 是否被强制关闭推送 */
        public Boolean getPushForcedOff() { return pushForcedOff; }
        /** @param pushForcedOff 是否被强制关闭推送 */
        public void setPushForcedOff(Boolean pushForcedOff) { this.pushForcedOff = pushForcedOff; }
        /** @return 提示文案 */
        public String getHint() { return hint; }
        /** @param hint 提示文案 */
        public void setHint(String hint) { this.hint = hint; }
    }

    /** 保存药品对照响应（IF-08）。 */
    public static class SaveDrugCodeMapResponse {

        /** 主键。 */
        private Long id;

        /** 是否已对照。 */
        private Boolean mapped;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
    }

    /** 导入汇总响应（IF-03/06/09）。 */
    public static class ImportMapResponse {

        /** 成功行数。 */
        private int successCount;

        /** 跳过行数。 */
        private int skipCount;

        /** 失败行数。 */
        private int failCount;

        /** 说明列表。 */
        private List<String> messages = new ArrayList<>();

        /** @return 成功行数 */
        public int getSuccessCount() { return successCount; }
        /** @param successCount 成功行数 */
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        /** @return 跳过行数 */
        public int getSkipCount() { return skipCount; }
        /** @param skipCount 跳过行数 */
        public void setSkipCount(int skipCount) { this.skipCount = skipCount; }
        /** @return 失败行数 */
        public int getFailCount() { return failCount; }
        /** @param failCount 失败行数 */
        public void setFailCount(int failCount) { this.failCount = failCount; }
        /** @return 说明列表 */
        public List<String> getMessages() { return messages; }
        /** @param messages 说明列表 */
        public void setMessages(List<String> messages) { this.messages = messages; }
    }

    /** 科室对照分页行 DTO。 */
    public static class DeptCodeMapRowDto {

        /** 主键。 */
        private Long id;

        /** 院内科室业务码。 */
        private String locationBusNo;

        /** 院内科室名称。 */
        private String locationName;

        /** 国家科室码。 */
        private String targetDeptCode;

        /** 国家科室名称。 */
        private String targetDeptName;

        /** 是否已对照。 */
        private Boolean mapped;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内科室业务码 */
        public String getLocationBusNo() { return locationBusNo; }
        /** @param locationBusNo 院内科室业务码 */
        public void setLocationBusNo(String locationBusNo) { this.locationBusNo = locationBusNo; }
        /** @return 院内科室名称 */
        public String getLocationName() { return locationName; }
        /** @param locationName 院内科室名称 */
        public void setLocationName(String locationName) { this.locationName = locationName; }
        /** @return 国家科室码 */
        public String getTargetDeptCode() { return targetDeptCode; }
        /** @param targetDeptCode 国家科室码 */
        public void setTargetDeptCode(String targetDeptCode) { this.targetDeptCode = targetDeptCode; }
        /** @return 国家科室名称 */
        public String getTargetDeptName() { return targetDeptName; }
        /** @param targetDeptName 国家科室名称 */
        public void setTargetDeptName(String targetDeptName) { this.targetDeptName = targetDeptName; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
    }

    /** 检验对照分页行 DTO。 */
    public static class LabItemMapRowDto {

        /** 主键。 */
        private Long id;

        /** 院内项目业务码。 */
        private String itemBusNo;

        /** 院内项目名称。 */
        private String itemName;

        /** 国家项目码。 */
        private String nationalItemCode;

        /** 结果形态。 */
        private String resultShape;

        /** 附件二范围。 */
        private String annex2Scope;

        /** 是否启用推送。 */
        private Boolean pushEnabled;

        /** 是否已对照。 */
        private Boolean mapped;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内项目业务码 */
        public String getItemBusNo() { return itemBusNo; }
        /** @param itemBusNo 院内项目业务码 */
        public void setItemBusNo(String itemBusNo) { this.itemBusNo = itemBusNo; }
        /** @return 院内项目名称 */
        public String getItemName() { return itemName; }
        /** @param itemName 院内项目名称 */
        public void setItemName(String itemName) { this.itemName = itemName; }
        /** @return 国家项目码 */
        public String getNationalItemCode() { return nationalItemCode; }
        /** @param nationalItemCode 国家项目码 */
        public void setNationalItemCode(String nationalItemCode) { this.nationalItemCode = nationalItemCode; }
        /** @return 结果形态 */
        public String getResultShape() { return resultShape; }
        /** @param resultShape 结果形态 */
        public void setResultShape(String resultShape) { this.resultShape = resultShape; }
        /** @return 附件二范围 */
        public String getAnnex2Scope() { return annex2Scope; }
        /** @param annex2Scope 附件二范围 */
        public void setAnnex2Scope(String annex2Scope) { this.annex2Scope = annex2Scope; }
        /** @return 是否启用推送 */
        public Boolean getPushEnabled() { return pushEnabled; }
        /** @param pushEnabled 是否启用推送 */
        public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
    }

    /** 药品对照分页行 DTO。 */
    public static class DrugCodeMapRowDto {

        /** 主键。 */
        private Long id;

        /** 院内药品业务码。 */
        private String medBusNo;

        /** 院内药品名称。 */
        private String medName;

        /** 国家药品码。 */
        private String nationalDrugCode;

        /** 国家药品名称。 */
        private String nationalDrugName;

        /** 是否已对照。 */
        private Boolean mapped;

        /** @return 主键 */
        public Long getId() { return id; }
        /** @param id 主键 */
        public void setId(Long id) { this.id = id; }
        /** @return 院内药品业务码 */
        public String getMedBusNo() { return medBusNo; }
        /** @param medBusNo 院内药品业务码 */
        public void setMedBusNo(String medBusNo) { this.medBusNo = medBusNo; }
        /** @return 院内药品名称 */
        public String getMedName() { return medName; }
        /** @param medName 院内药品名称 */
        public void setMedName(String medName) { this.medName = medName; }
        /** @return 国家药品码 */
        public String getNationalDrugCode() { return nationalDrugCode; }
        /** @param nationalDrugCode 国家药品码 */
        public void setNationalDrugCode(String nationalDrugCode) { this.nationalDrugCode = nationalDrugCode; }
        /** @return 国家药品名称 */
        public String getNationalDrugName() { return nationalDrugName; }
        /** @param nationalDrugName 国家药品名称 */
        public void setNationalDrugName(String nationalDrugName) { this.nationalDrugName = nationalDrugName; }
        /** @return 是否已对照 */
        public Boolean getMapped() { return mapped; }
        /** @param mapped 是否已对照 */
        public void setMapped(Boolean mapped) { this.mapped = mapped; }
    }
}
