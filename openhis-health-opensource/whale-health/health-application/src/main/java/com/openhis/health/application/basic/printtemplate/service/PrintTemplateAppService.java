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

package com.openhis.health.application.basic.printtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.print.support.PrintJsonSupport;
import com.openhis.health.application.basic.print.support.PrintOperatorResolver;
import com.openhis.health.application.basic.printtemplate.dto.PrtTemplateDto;
import com.openhis.health.application.basic.printtemplate.dto.PrtTemplateListItemDto;
import com.openhis.health.application.basic.printtemplate.request.CreateBlankDraftRequest;
import com.openhis.health.application.basic.printtemplate.request.DeleteTemplateRequest;
import com.openhis.health.application.basic.printtemplate.request.ForkDraftRequest;
import com.openhis.health.application.basic.printtemplate.request.GetByBusNoRequest;
import com.openhis.health.application.basic.printtemplate.request.PrtTemplateQueryRequest;
import com.openhis.health.application.basic.printtemplate.request.PublishTemplateRequest;
import com.openhis.health.application.basic.printtemplate.request.SaveDraftRequest;
import com.openhis.health.application.basic.printtemplate.response.QueryPrtTemplatePageResponse;
import com.openhis.health.domain.custom.printtemplate.command.ForkDraftFromPublishedCommand;
import com.openhis.health.domain.custom.printtemplate.command.PublishTemplateCommand;
import com.openhis.health.domain.custom.printtemplate.command.SaveDraftCommand;
import com.openhis.health.domain.custom.printtemplate.constants.PrintTemplateStatus;
import com.openhis.health.domain.custom.printtemplate.constants.PrtTenantScope;
import com.openhis.health.domain.custom.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.custom.printtemplate.service.PrintJsonValidator;
import com.openhis.health.domain.custom.printtemplate.service.PrtBusNoGenerator;
import com.openhis.health.domain.custom.printtemplate.service.PrtTemplateManager;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 画布打印模板应用服务（IF-BAS-PRT-11～16）。
 */
@Service
@RequiredArgsConstructor
public class PrintTemplateAppService {

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final PrtTemplateManager prtTemplateManager;
  private final PrtBusNoGenerator prtBusNoGenerator;
  private final PrintJsonValidator printJsonValidator;
  private final PrintOperatorResolver printOperatorResolver;
  private final PrintJsonSupport printJsonSupport;

  /**
   * 模板分页列表（IF-BAS-PRT-11）：按 busNo 聚合，每种逻辑模板一行。
   *
   * @param request 分页与筛选
   * @return 分页列表
   */
  public QueryPrtTemplatePageResponse queryPage(PrtTemplateQueryRequest request) {
    PrtTemplateQueryRequest safeRequest = request == null ? new PrtTemplateQueryRequest() : request;
    Long tenantId = printOperatorResolver.requireTenantId();
    String statusFilter = trimToNull(safeRequest.getStatus());
    boolean includeArchived =
        statusFilter != null
            && PrintTemplateStatus.ARCHIVED.equals(statusFilter.toLowerCase(Locale.ROOT));
    List<PrtTemplate> merged =
        prtTemplateManager.listMergedForTenant(tenantId, includeArchived);
    List<PrtTemplateListItemDto> aggregated = aggregateByBusNo(merged, tenantId);
    String keyword = trimToNull(safeRequest.getKeyword());
    List<PrtTemplateListItemDto> rows = new ArrayList<>();
    for (PrtTemplateListItemDto row : aggregated) {
      if (statusFilter != null && !includeArchived) {
        if (!row.getStatus().equalsIgnoreCase(statusFilter)) {
          continue;
        }
      }
      if (keyword != null) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        boolean busMatch =
            row.getBusNo() != null && row.getBusNo().toLowerCase(Locale.ROOT).contains(lower);
        boolean nameMatch =
            row.getName() != null && row.getName().toLowerCase(Locale.ROOT).contains(lower);
        boolean scenarioMatch =
            row.getScenarioCode() != null
                && row.getScenarioCode().toLowerCase(Locale.ROOT).contains(lower);
        if (!busMatch && !nameMatch && !scenarioMatch) {
          continue;
        }
      }
      rows.add(row);
    }
    int pageNo = resolvePageNo(safeRequest.getPageNo());
    int pageSize = resolvePageSize(safeRequest.getPageSize());
    return PagedResultBuilder.buildFromFullList(
        new QueryPrtTemplatePageResponse(), rows, pageNo, pageSize);
  }

  /**
   * 新建空白草稿（IF-BAS-PRT-12）：busNo 由后端生成（PT+日期+序号）。
   *
   * @param request 可选名称
   * @return 草稿详情
   */
  public PrtTemplateDto createBlankDraft(CreateBlankDraftRequest request) {
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    String busNo = prtBusNoGenerator.nextBusNo(tenantId);
    printJsonValidator.validate(PrintJsonSupport.DEFAULT_BLANK_PRINT_JSON);
    SaveDraftCommand cmd = new SaveDraftCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    String name = trimToNull(request == null ? null : request.getName());
    cmd.setName(name != null ? name : busNo);
    cmd.setScenarioCode(trimToNull(request == null ? null : request.getScenarioCode()));
    cmd.setPrintJson(PrintJsonSupport.DEFAULT_BLANK_PRINT_JSON);
    PrtTemplate saved = prtTemplateManager.saveDraft(cmd);
    return toDetailDto(saved);
  }

  /**
   * 按 busNo 加载设计态模板（IF-BAS-PRT-13）。
   *
   * @param request busNo
   * @return 模板详情
   */
  public PrtTemplateDto getByBusNo(GetByBusNoRequest request) {
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    PrtTemplate template = prtTemplateManager.resolveForDesign(busNo, tenantId);
    if (template == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + busNo);
    }
    return toDetailDto(template);
  }

  /**
   * 进入设计器前确保存在草稿（无 draft 时从 published fork）。
   *
   * @param request busNo
   * @return 草稿详情
   */
  public PrtTemplateDto ensureDesignDraft(GetByBusNoRequest request) {
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    PrtTemplate draft = prtTemplateManager.ensureDesignDraft(busNo, tenantId);
    return toDetailDto(draft);
  }

  /**
   * 保存草稿（IF-BAS-PRT-14）。
   *
   * @param request 草稿载荷
   * @return 保存后详情
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
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    printJsonValidator.validate(printJson);
    PrtTemplate existingDraft = null;
    if (draftId != null) {
      existingDraft = prtTemplateManager.findDraftById(draftId, tenantId);
      if (existingDraft == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到草稿模板");
      }
      busNo = existingDraft.getBusNo();
    } else {
      existingDraft = prtTemplateManager.findTenantDraft(busNo, tenantId);
    }
    SaveDraftCommand cmd = new SaveDraftCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(busNo);
    cmd.setName(trimToNull(request.getName()));
    cmd.setScenarioCode(trimToNull(request.getScenarioCode()));
    cmd.setPrintJson(printJson);
    cmd.setSamplePrintData(printJsonSupport.toJsonString(request.getSamplePrintData()));
    if (existingDraft != null) {
      cmd.setExistingId(existingDraft.getId());
    }
    PrtTemplate saved = prtTemplateManager.saveDraft(cmd);
    return toDetailDto(saved);
  }

  /**
   * 发布模板（IF-BAS-PRT-15）。
   *
   * @param request busNo
   * @return 发布后详情
   */
  public PrtTemplateDto publish(PublishTemplateRequest request) {
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    PrtTemplate working = prtTemplateManager.findTenantDraft(busNo, tenantId);
    if (working == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "无草稿可发布，请先设计并保存");
    }
    printJsonValidator.validate(working.getPrintJson());
    PublishTemplateCommand publishCmd = new PublishTemplateCommand();
    publishCmd.setDraftId(working.getId());
    publishCmd.setTenantId(tenantId);
    publishCmd.setBusNo(busNo);
    PrtTemplate published = prtTemplateManager.publish(publishCmd);
    return toDetailDto(published);
  }

  /**
   * 从 published 复制草稿（IF-BAS-PRT-16）。
   *
   * @param request busNo
   * @return 草稿详情
   */
  public PrtTemplateDto forkDraft(ForkDraftRequest request) {
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    PrtTemplate draft = prtTemplateManager.ensureDesignDraft(busNo, tenantId);
    return toDetailDto(draft);
  }

  /**
   * 删除租户模板（IF-BAS-PRT-17）：软删该 busNo 下全部物理行。
   *
   * @param request busNo
   */
  public void deleteTemplate(DeleteTemplateRequest request) {
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    Long tenantId = printOperatorResolver.requireTenantId();
    assertTenantCanEditDraft(tenantId);
    prtTemplateManager.deleteByBusNo(busNo, tenantId);
  }

  private List<PrtTemplateListItemDto> aggregateByBusNo(List<PrtTemplate> merged, Long tenantId) {
    Map<String, List<PrtTemplate>> grouped = new HashMap<>();
    for (PrtTemplate row : merged) {
      if (row.getBusNo() == null) {
        continue;
      }
      grouped.computeIfAbsent(row.getBusNo(), key -> new ArrayList<>()).add(row);
    }
    List<PrtTemplateListItemDto> result = new ArrayList<>();
    for (Map.Entry<String, List<PrtTemplate>> entry : grouped.entrySet()) {
      PrtTemplateListItemDto dto = toAggregatedListItem(entry.getKey(), entry.getValue(), tenantId);
      if (dto != null) {
        result.add(dto);
      }
    }
    result.sort(
        (left, right) -> {
          String leftBusNo = left.getBusNo() == null ? "" : left.getBusNo();
          String rightBusNo = right.getBusNo() == null ? "" : right.getBusNo();
          return leftBusNo.compareToIgnoreCase(rightBusNo);
        });
    return result;
  }

  private PrtTemplateListItemDto toAggregatedListItem(
      String busNo, List<PrtTemplate> rows, Long tenantId) {
    PrtTemplate draft = null;
    PrtTemplate published = null;
    PrtTemplate latestArchived = null;
    boolean tenantOwned = false;
    for (PrtTemplate row : rows) {
      if (tenantId != null && tenantId.equals(row.getTenantId())) {
        tenantOwned = true;
      }
      if (PrintTemplateStatus.DRAFT.equals(row.getStatus())) {
        if (draft == null) {
          draft = row;
        }
        continue;
      }
      if (PrintTemplateStatus.PUBLISHED.equals(row.getStatus())) {
        if (published == null
            || (row.getVersion() != null
                && (published.getVersion() == null || row.getVersion() > published.getVersion()))) {
          published = row;
        }
        continue;
      }
      if (PrintTemplateStatus.ARCHIVED.equals(row.getStatus())) {
        if (latestArchived == null
            || (row.getVersion() != null
                && (latestArchived.getVersion() == null
                    || row.getVersion() > latestArchived.getVersion()))) {
          latestArchived = row;
        }
      }
    }
    if (draft == null && published == null && latestArchived == null) {
      return null;
    }
    PrtTemplateListItemDto dto = new PrtTemplateListItemDto();
    dto.setBusNo(busNo);
    dto.setHasDraft(draft != null);
    dto.setDraftId(draft == null ? null : draft.getId());
    dto.setPublishedId(published == null ? null : published.getId());
    dto.setScenarioCode(resolveAggregatedScenarioCode(draft, published, latestArchived));
    if (draft != null) {
      dto.setStatus(PrintTemplateStatus.DRAFT);
      dto.setId(draft.getId());
      dto.setName(draft.getName());
      dto.setSchemaVersion(draft.getSchemaVersion());
    } else if (published != null) {
      dto.setStatus(PrintTemplateStatus.PUBLISHED);
      dto.setId(published.getId());
      dto.setName(published.getName());
      dto.setSchemaVersion(published.getSchemaVersion());
    } else {
      dto.setStatus(PrintTemplateStatus.ARCHIVED);
      dto.setId(latestArchived.getId());
      dto.setName(latestArchived.getName());
      dto.setSchemaVersion(latestArchived.getSchemaVersion());
    }
    dto.setVersion(published == null ? null : published.getVersion());
    dto.setDeletable(tenantOwned && PrtTenantScope.PLATFORM_TENANT_ID != tenantId);
    return dto;
  }

  private String resolveAggregatedScenarioCode(
      PrtTemplate draft, PrtTemplate published, PrtTemplate latestArchived) {
    if (draft != null && trimToNull(draft.getScenarioCode()) != null) {
      return draft.getScenarioCode();
    }
    if (published != null && trimToNull(published.getScenarioCode()) != null) {
      return published.getScenarioCode();
    }
    if (latestArchived != null) {
      return trimToNull(latestArchived.getScenarioCode());
    }
    return null;
  }

  private void assertTenantCanEditDraft(Long tenantId) {
    if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId) {
      throw new ValidationException("平台租户不允许编辑草稿");
    }
  }

  private PrtTemplateDto toDetailDto(PrtTemplate template) {
    PrtTemplateDto dto = new PrtTemplateDto();
    dto.setId(template.getId());
    dto.setBusNo(template.getBusNo());
    dto.setScenarioCode(template.getScenarioCode());
    dto.setName(template.getName());
    dto.setStatus(template.getStatus());
    dto.setVersion(template.getVersion());
    dto.setSchemaVersion(template.getSchemaVersion());
    dto.setPrintJson(printJsonSupport.parseJsonOrNull(template.getPrintJson()));
    dto.setSamplePrintData(printJsonSupport.parseJsonOrNull(template.getSamplePrintData()));
    return dto;
  }

  private int resolvePageNo(Integer pageNo) {
    if (pageNo == null || pageNo < 1) {
      return 1;
    }
    return pageNo;
  }

  private int resolvePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, 1000);
  }
}
