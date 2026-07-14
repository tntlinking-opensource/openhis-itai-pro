/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printtemplate.dto.PrtTemplateDto;
import com.openhis.health.application.print.printtemplate.dto.PrtTemplateListItemDto;
import com.openhis.health.application.print.printtemplate.mapper.PrintTemplateMapper;
import com.openhis.health.application.print.printtemplate.request.CreateBlankDraftRequest;
import com.openhis.health.application.print.printtemplate.request.DeleteTemplateRequest;
import com.openhis.health.application.print.printtemplate.request.ForkDraftRequest;
import com.openhis.health.application.print.printtemplate.request.GetByBusNoRequest;
import com.openhis.health.application.print.printtemplate.request.PrtTemplateQueryRequest;
import com.openhis.health.application.print.printtemplate.request.PublishTemplateRequest;
import com.openhis.health.application.print.printtemplate.request.SaveDraftRequest;
import com.openhis.health.application.print.printtemplate.response.QueryPrtTemplatePageResponse;
import com.openhis.health.application.print.printtemplate.support.PrintTemplateListAssembler;
import com.openhis.health.application.print.printtemplate.support.PrintJsonHelper;
import com.openhis.health.application.shared.validator.PrintOperatorResolver;
import com.openhis.health.domain.print.printtemplate.command.ForkDraftFromPublishedCommand;
import com.openhis.health.domain.print.printtemplate.command.PublishTemplateCommand;
import com.openhis.health.domain.print.printtemplate.command.SaveDraftCommand;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.service.PrtTemplateDomainService;
import com.openhis.health.domain.print.printtemplate.service.PrtTemplateQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.health.domain.share.enums.PrintTemplateStatus;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 画布打印模板应用服务（AS-BAS-PRT，IF-BAS-PRT-11～17）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>租户模板设计态 CRUD、发布与列表聚合视图；</li>
 *   <li>写路径经 {@link PrtTemplateDomainService}，读路径经 {@link PrtTemplateQueryService}；</li>
 *   <li>出参经 {@link PrintTemplateMapper} 与 {@link PrintTemplateListAssembler} 装配；</li>
 *   <li>会话租户与操作人经 {@link com.openhis.health.application.shared.validator.PrintOperatorResolver} 解析。</li>
 * </ul>
 *
 * <p>由 AppServiceControllerRegistrar 暴露 REST，本类不含 Controller。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateAppService {

  /** 模板写侧：草稿保存、发布、fork、删除。 */
  private final PrtTemplateDomainService prtTemplateDomainService;

  /** 模板读侧：合并列表、设计态解析、按 id/busNo 查草稿。 */
  private final PrtTemplateQueryService prtTemplateQueryService;

  /** PrintJson 序列化/反序列化、校验与 hash。 */
  private final PrintJsonHelper printJsonHelper;

  /** 会话租户与操作人解析。 */
  private final PrintOperatorResolver printOperatorResolver;

  /** 模板详情与 Command 装配。 */
  private final PrintTemplateMapper printTemplateMapper;

  /** 列表 busNo 聚合与内存筛选。 */
  private final PrintTemplateListAssembler printTemplateListAssembler;

  /**
   * 模板分页列表（IF-BAS-PRT-11）：按 busNo 聚合，每种逻辑模板一行。
   *
   * @param request 分页与筛选；可 null（使用默认分页）
   * @return 聚合后的模板分页
   */
  public QueryPrtTemplatePageResponse queryPage(PrtTemplateQueryRequest request) {
    // Gate：须已选租户，列表仅展示当前租户可见模板（含平台 fallback 合并）。
    PrtTemplateQueryRequest safeRequest = normalizeQueryRequest(request);
    Long tenantId = printOperatorResolver.requireTenantId();
    // Pre：合并平台与租户物理行，再按 busNo 聚合成逻辑模板视图。
    String statusFilter = trimToNull(safeRequest.getStatus());
    boolean includeArchived = isArchivedStatusFilter(statusFilter);
    List<PrtTemplate> merged =
        prtTemplateQueryService.listMergedForTenant(tenantId, includeArchived);
    List<PrtTemplateListItemDto> aggregated =
        printTemplateListAssembler.aggregateByBusNo(merged, tenantId);
    // Pre：内存过滤 status / keyword（聚合后字段与 DB 行不完全同构）。
    String keyword = trimToNull(safeRequest.getKeyword());
    List<PrtTemplateListItemDto> rows =
        printTemplateListAssembler.filterAggregatedRows(
            aggregated, statusFilter, includeArchived, keyword);
    // Asm：内存分页（全量聚合后再切片，与 IF 约定一致）。
    int pageNo = resolvePageNo(safeRequest.getPageNo());
    int pageSize = resolvePageSize(safeRequest.getPageSize());
    return PagedResultBuilder.buildFromFullList(
        new QueryPrtTemplatePageResponse(), rows, pageNo, pageSize);
  }

  /**
   * 新建空白草稿（IF-BAS-PRT-12）：busNo 由后端按日序号生成。
   *
   * @param request 可选名称与场景码；可 null
   * @return 新建草稿详情
   * @throws ValidationException 平台租户不可编辑草稿
   */
  public PrtTemplateDto createBlankDraft(CreateBlankDraftRequest request) {
    // Gate：平台租户仅维护出厂 published，不可新建租户草稿。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Pre：生成 busNo 并校验默认空白版式合法。
    String busNo = prtTemplateDomainService.nextBusNo(tenantId);
    printJsonHelper.validatePrintJson(PrintConstants.DEFAULT_BLANK_PRINT_JSON);
    // Cmd：Mapper 装配 SaveDraftCommand 并持久化。
    SaveDraftCommand cmd =
        printTemplateMapper.ConverterBlankDraftCommand(
            tenantId,
            busNo,
            resolveDraftName(request, busNo),
            PrintConstants.DEFAULT_BLANK_PRINT_JSON);
    PrtTemplate saved = prtTemplateDomainService.saveDraft(cmd);
    // Asm：结构化 PrintJson 出参。
    return printTemplateMapper.ConverterPrtTemplateDto(saved);
  }

  /**
   * 按 busNo 加载设计态模板（IF-BAS-PRT-13）：draft 优先，否则 published。
   *
   * @param request 含 busNo
   * @return 模板详情
   * @throws ValidationException busNo 为空
   * @throws BusinessException 模板不存在
   */
  public PrtTemplateDto getByBusNo(GetByBusNoRequest request) {
    String busNo = extractBusNo(request);
    // Gate：须已选租户。
    Long tenantId = printOperatorResolver.requireTenantId();
    // Pre：设计态解析（draft 覆盖 published）。
    PrtTemplate template = prtTemplateQueryService.resolveForDesign(busNo, tenantId);
    if (template == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + busNo);
    }
    return printTemplateMapper.ConverterPrtTemplateDto(template);
  }

  /**
   * 进入设计器前确保存在草稿（IF-BAS-PRT-13 扩展）：无 draft 时从 published fork。
   *
   * @param request 含 busNo
   * @return 可编辑草稿详情
   * @throws ValidationException busNo 为空或平台租户不可编辑
   * @throws BusinessException 无 published 可 fork
   */
  public PrtTemplateDto ensureDesignDraft(GetByBusNoRequest request) {
    String busNo = extractBusNo(request);
    // Gate：平台租户不可编辑草稿。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Cmd：App 编排 fork 幂等（SDD §5.1.3 · 已有 draft 则直接返回）。
    PrtTemplate draft = resolveOrForkDesignDraft(busNo, tenantId);
    return printTemplateMapper.ConverterPrtTemplateDto(draft);
  }

  /**
   * 保存草稿（IF-BAS-PRT-14）。
   *
   * @param request 草稿载荷；id 与 busNo 至少提供一个
   * @return 保存后详情
   * @throws ValidationException 请求非法或 PrintJson 为空
   * @throws BusinessException 草稿不存在
   */
  public PrtTemplateDto saveDraft(SaveDraftRequest request) {
    if (request == null) {
      throw new ValidationException("请求不能为空");
    }
    Long draftId = request.getId();
    String busNo = trimToNull(request.getBusNo());
    if (draftId == null && busNo == null) {
      throw new ValidationException("id 与 busNo 须至少提供一个");
    }
    String printJson = printJsonHelper.toJsonString(request.getPrintJson());
    if (printJson == null) {
      throw new ValidationException("PrintJson 不能为空");
    }
    // Gate：平台租户不可保存草稿。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Pre：版式校验 + 定位已有草稿行。
    printJsonHelper.validatePrintJson(printJson);
    PrtTemplate existingDraft = resolveExistingDraft(draftId, busNo, tenantId);
    if (draftId != null && existingDraft == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到草稿模板");
    }
    Long existingDraftId = null;
    if (existingDraft != null) {
      busNo = existingDraft.getBusNo();
      existingDraftId = existingDraft.getId();
    }
    // Cmd：Mapper 装配命令并写库。
    SaveDraftCommand cmd =
        printTemplateMapper.ConverterSaveDraftCommand(
            request,
            tenantId,
            busNo,
            printJson,
            printJsonHelper.toJsonString(request.getSamplePrintData()),
            existingDraftId);
    PrtTemplate saved = prtTemplateDomainService.saveDraft(cmd);
    return printTemplateMapper.ConverterPrtTemplateDto(saved);
  }

  /**
   * 发布模板（IF-BAS-PRT-15）：将租户 draft 发布为新 published 行。
   *
   * @param request 含 busNo
   * @return 发布后详情（published 行）
   * @throws ValidationException busNo 为空或平台租户不可编辑
   * @throws BusinessException 无草稿可发布
   */
  public PrtTemplateDto publish(PublishTemplateRequest request) {
    String busNo = extractBusNoFromPublishRequest(request);
    // Gate：平台租户不可发布。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Pre：须存在可发布草稿。
    PrtTemplate working = prtTemplateQueryService.findTenantDraft(busNo, tenantId);
    if (working == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "无草稿可发布，请先设计并保存");
    }
    printJsonHelper.validatePrintJson(working.getPrintJson());
    // Cmd：发布命令（旧 published 归档、源 draft 软删）。
    PublishTemplateCommand publishCmd =
        printTemplateMapper.ConverterPublishTemplateCommand(working.getId(), tenantId, busNo);
    PrtTemplate published = prtTemplateDomainService.publish(publishCmd);
    return printTemplateMapper.ConverterPrtTemplateDto(published);
  }

  /**
   * 从 published 复制草稿（IF-BAS-PRT-16）：等价 ensureDesignDraft。
   *
   * @param request 含 busNo
   * @return 草稿详情
   * @throws ValidationException busNo 为空或平台租户不可编辑
   * @throws BusinessException 无 published 可 fork
   */
  public PrtTemplateDto forkDraft(ForkDraftRequest request) {
    String busNo = extractBusNoFromForkRequest(request);
    // Gate：平台租户不可 fork。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Cmd：App 编排 fork 幂等（SDD §5.1.3 · 已有 draft 则直接返回）。
    PrtTemplate draft = resolveOrForkDesignDraft(busNo, tenantId);
    return printTemplateMapper.ConverterPrtTemplateDto(draft);
  }

  /**
   * 删除租户模板（IF-BAS-PRT-17）：软删该 busNo 下全部物理行。
   *
   * @param request 含 busNo
   * @throws ValidationException busNo 为空或平台租户不可删
   */
  public void deleteTemplate(DeleteTemplateRequest request) {
    String busNo = extractBusNoFromDeleteRequest(request);
    // Gate：平台租户不可删除租户模板。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Cmd：按 busNo 软删租户全部物理行。
    prtTemplateDomainService.deleteByBusNo(busNo, tenantId);
  }

  /**
   * 列表请求 null 安全：缺省则新建空查询对象。
   *
   * @param request 原始请求
   * @return 非 null 查询对象
   */
  private PrtTemplateQueryRequest normalizeQueryRequest(PrtTemplateQueryRequest request) {
    if (request == null) {
      return new PrtTemplateQueryRequest();
    }
    return request;
  }

  /**
   * 状态筛选是否为 archived（大小写不敏感）。
   *
   * @param statusFilter 聚合后 status 筛选
   * @return archived 为 true
   */
  private boolean isArchivedStatusFilter(String statusFilter) {
    if (statusFilter == null) {
      return false;
    }
    return PrintTemplateStatus.ARCHIVED.sameAs(statusFilter.toLowerCase(Locale.ROOT));
  }

  /**
   * 空白草稿默认名称：请求 name 非空则用，否则回落 busNo。
   *
   * @param request 创建请求
   * @param busNo   已生成业务号
   * @return 草稿名称
   */
  private String resolveDraftName(CreateBlankDraftRequest request, String busNo) {
    String name = null;
    if (request != null) {
      name = trimToNull(request.getName());
    }
    if (name == null) {
      name = busNo;
    }
    return name;
  }

  /**
   * 设计态草稿：已有 tenant draft 则返回，否则从 published fork。
   *
   * @param busNo    模板业务号
   * @param tenantId 租户
   * @return 可编辑草稿实体
   * @throws BusinessException 无 published 可 fork
   */
  private PrtTemplate resolveOrForkDesignDraft(String busNo, Long tenantId) {
    PrtTemplate draft = prtTemplateQueryService.findTenantDraft(busNo, tenantId);
    if (draft != null) {
      return draft;
    }
    ForkDraftFromPublishedCommand cmd = new ForkDraftFromPublishedCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    return prtTemplateDomainService.forkDraftFromPublished(cmd);
  }

  /**
   * 定位待保存草稿：优先 draftId，否则按 busNo 查 tenant draft。
   *
   * @param draftId  草稿主键（可空）
   * @param busNo    业务号（可空）
   * @param tenantId 租户
   * @return 已有草稿；均未命中则 null
   */
  private PrtTemplate resolveExistingDraft(Long draftId, String busNo, Long tenantId) {
    if (draftId != null) {
      return prtTemplateQueryService.findDraftById(draftId, tenantId);
    }
    return prtTemplateQueryService.findTenantDraft(busNo, tenantId);
  }

  /**
   * 从 GetByBusNo 请求提取 busNo 并校验非空。
   *
   * @param request 查询请求
   * @return 去空白后的 busNo
   * @throws ValidationException busNo 为空
   */
  private String extractBusNo(GetByBusNoRequest request) {
    String busNo = null;
    if (request != null) {
      busNo = trimToNull(request.getBusNo());
    }
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    return busNo;
  }

  /**
   * 从发布请求提取 busNo 并校验非空。
   *
   * @param request 发布请求
   * @return 去空白后的 busNo
   * @throws ValidationException busNo 为空
   */
  private String extractBusNoFromPublishRequest(PublishTemplateRequest request) {
    String busNo = null;
    if (request != null) {
      busNo = trimToNull(request.getBusNo());
    }
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    return busNo;
  }

  /**
   * 从 fork 请求提取 busNo 并校验非空。
   *
   * @param request fork 请求
   * @return 去空白后的 busNo
   * @throws ValidationException busNo 为空
   */
  private String extractBusNoFromForkRequest(ForkDraftRequest request) {
    String busNo = null;
    if (request != null) {
      busNo = trimToNull(request.getBusNo());
    }
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    return busNo;
  }

  /**
   * 从删除请求提取 busNo 并校验非空。
   *
   * @param request 删除请求
   * @return 去空白后的 busNo
   * @throws ValidationException busNo 为空
   */
  private String extractBusNoFromDeleteRequest(DeleteTemplateRequest request) {
    String busNo = null;
    if (request != null) {
      busNo = trimToNull(request.getBusNo());
    }
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    return busNo;
  }

  /**
   * 平台租户（tenantId=0）禁止编辑/发布/删除租户草稿。
   *
   * @param tenantId 当前租户
   * @throws ValidationException 平台租户
   */
  private void assertTenantCanEditDraft(Long tenantId) {
    if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId) {
      throw new ValidationException("平台租户不允许编辑草稿");
    }
  }

  /**
   * 对外 pageNo 归一：null 或 &lt;1 则 1。
   *
   * @param pageNo 请求页码
   * @return 1-based 页码
   */
  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  /**
   * 对外 pageSize 归一：null 或 &lt;1 用默认，上限 {@link PrintConstants#PAGE_SIZE_MAX}。
   *
   * @param pageSize 请求页大小
   * @return 合法页大小
   */
  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }
    return Math.min(pageSize, PrintConstants.PAGE_SIZE_MAX);
  }
}
