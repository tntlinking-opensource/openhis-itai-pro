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
import com.openhis.health.application.print.printtemplate.support.PrintJsonSupport;
import com.openhis.health.module.common.validator.PrintOperatorResolver;
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
import com.openhis.health.module.common.pagination.PagedResultBuilder;
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
 *   <li>会话租户与操作人经 {@link com.openhis.health.module.common.validator.PrintOperatorResolver} 解析。</li>
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
  private final PrintJsonSupport printJsonSupport;

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
    printJsonSupport.validatePrintJson(PrintConstants.DEFAULT_BLANK_PRINT_JSON);
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
    String printJson = printJsonSupport.toJsonString(request.getPrintJson());
    if (printJson == null) {
      throw new ValidationException("PrintJson 不能为空");
    }
    // Gate：平台租户不可保存草稿。
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    // Pre：版式校验 + 定位已有草稿行。
    printJsonSupport.validatePrintJson(printJson);
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
            printJsonSupport.toJsonString(request.getSamplePrintData()),
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
    printJsonSupport.validatePrintJson(working.getPrintJson());
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

  private PrtTemplateQueryRequest normalizeQueryRequest(PrtTemplateQueryRequest request) {
    if (request == null) {
      return new PrtTemplateQueryRequest();
    }
    return request;
  }

  private boolean isArchivedStatusFilter(String statusFilter) {
    if (statusFilter == null) {
      return false;
    }
    return PrintTemplateStatus.ARCHIVED.sameAs(statusFilter.toLowerCase(Locale.ROOT));
  }

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

  private PrtTemplate resolveExistingDraft(Long draftId, String busNo, Long tenantId) {
    if (draftId != null) {
      return prtTemplateQueryService.findDraftById(draftId, tenantId);
    }
    return prtTemplateQueryService.findTenantDraft(busNo, tenantId);
  }

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

  private void assertTenantCanEditDraft(Long tenantId) {
    if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId) {
      throw new ValidationException("平台租户不允许编辑草稿");
    }
  }

  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }
    return Math.min(pageSize, PrintConstants.PAGE_SIZE_MAX);
  }
}
