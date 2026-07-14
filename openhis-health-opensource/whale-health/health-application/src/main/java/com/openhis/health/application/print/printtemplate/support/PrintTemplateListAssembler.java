/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printtemplate.dto.PrtTemplateListItemDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.PrintTemplateStatus;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 模板列表聚合：将平台+租户物理行按 busNo 折叠为逻辑模板行，并支持内存筛选。
 *
 * <p>仅服务 IF-BAS-PRT-11 列表视图，不含写路径判断。</p>
 */
@Component
public class PrintTemplateListAssembler {

  /**
   * 将平台+租户物理行按 busNo 聚合为列表行（draft 优先展示态）。
   *
   * @param merged 合并后的物理行
   * @param tenantId 当前租户
   * @return 聚合列表，按 busNo 升序
   */
  public List<PrtTemplateListItemDto> aggregateByBusNo(List<PrtTemplate> merged, Long tenantId) {
    Map<String, List<PrtTemplate>> grouped = new HashMap<>();
    for (PrtTemplate row : merged) {
      // 无 busNo 的脏行不参与聚合。
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
          String leftBusNo = "";
          if (left.getBusNo() != null) {
            leftBusNo = left.getBusNo();
          }
          String rightBusNo = "";
          if (right.getBusNo() != null) {
            rightBusNo = right.getBusNo();
          }
          return leftBusNo.compareToIgnoreCase(rightBusNo);
        });
    return result;
  }

  /**
   * 对聚合结果做 status/keyword 内存过滤。
   *
   * @param aggregated 聚合行
   * @param statusFilter 状态筛选；可 null
   * @param includeArchived 是否 archived 专查模式
   * @param keyword 关键字；可 null
   * @return 过滤后的行
   */
  public List<PrtTemplateListItemDto> filterAggregatedRows(
      List<PrtTemplateListItemDto> aggregated,
      String statusFilter,
      boolean includeArchived,
      String keyword) {
    List<PrtTemplateListItemDto> rows = new ArrayList<>();
    for (PrtTemplateListItemDto row : aggregated) {
      // 非 archived 专查时，按聚合展示态过滤。
      if (statusFilter != null && !includeArchived) {
        if (!row.getStatus().equalsIgnoreCase(statusFilter)) {
          continue;
        }
      }
      if (keyword != null && !matchesKeyword(row, keyword)) {
        continue;
      }
      rows.add(row);
    }
    return rows;
  }

  /**
   * 打印模板聚合列表关键字匹配：业务号、名称、场景码任一包含（不区分大小写）。
   *
   * @param row     聚合行
   * @param keyword 已 trim 的小写关键字
   * @return 命中为 true
   */
  private boolean matchesKeyword(PrtTemplateListItemDto row, String keyword) {
    String lower = keyword.toLowerCase(Locale.ROOT);
    boolean busMatch =
        row.getBusNo() != null && row.getBusNo().toLowerCase(Locale.ROOT).contains(lower);
    boolean nameMatch =
        row.getName() != null && row.getName().toLowerCase(Locale.ROOT).contains(lower);
    boolean scenarioMatch =
        row.getScenarioCode() != null
            && row.getScenarioCode().toLowerCase(Locale.ROOT).contains(lower);
    return busMatch || nameMatch || scenarioMatch;
  }

  /**
   * 同一 busNo 下 draft/published/archived 行聚合为列表项（展示态 draft &gt; published &gt; archived）。
   *
   * @param busNo    模板业务号
   * @param rows     同 busNo 的物理行
   * @param tenantId 当前租户（判定是否可删）
   * @return 聚合 DTO；三种状态均无有效行时 null
   */
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
      if (PrintTemplateStatus.DRAFT.sameAs(row.getStatus())) {
        if (draft == null) {
          draft = row;
        }
        continue;
      }
      if (PrintTemplateStatus.PUBLISHED.sameAs(row.getStatus())) {
        if (published == null || isHigherVersion(row, published)) {
          published = row;
        }
        continue;
      }
      if (PrintTemplateStatus.ARCHIVED.sameAs(row.getStatus())) {
        if (latestArchived == null || isHigherVersion(row, latestArchived)) {
          latestArchived = row;
        }
      }
    }
    // 三种状态均无有效行时跳过该 busNo。
    if (draft == null && published == null && latestArchived == null) {
      return null;
    }
    PrtTemplateListItemDto dto = new PrtTemplateListItemDto();
    dto.setBusNo(busNo);
    dto.setHasDraft(draft != null);
    if (draft != null) {
      dto.setDraftId(draft.getId());
    }
    if (published != null) {
      dto.setPublishedId(published.getId());
    }
    dto.setScenarioCode(resolveAggregatedScenarioCode(draft, published, latestArchived));
    // 展示态优先级：draft > published > archived。
    if (draft != null) {
      dto.setStatus(PrintTemplateStatus.DRAFT.getCode());
      dto.setId(draft.getId());
      dto.setName(draft.getName());
      dto.setSchemaVersion(draft.getSchemaVersion());
    } else if (published != null) {
      dto.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
      dto.setId(published.getId());
      dto.setName(published.getName());
      dto.setSchemaVersion(published.getSchemaVersion());
    } else {
      dto.setStatus(PrintTemplateStatus.ARCHIVED.getCode());
      dto.setId(latestArchived.getId());
      dto.setName(latestArchived.getName());
      dto.setSchemaVersion(latestArchived.getSchemaVersion());
    }
    if (published != null) {
      dto.setVersion(published.getVersion());
    }
    // 平台出厂模板不可删；租户自有模板可删。
    dto.setDeletable(tenantOwned && GeneralSharedConstants.PLATFORM_TENANT_ID != tenantId);
    return dto;
  }

  /**
   * 比较两物理行版本号：candidate 版本更高时为 true（null 版本视为更低）。
   *
   * @param candidate 候选行
   * @param current   当前最优行
   * @return candidate 更优为 true
   */
  private boolean isHigherVersion(PrtTemplate candidate, PrtTemplate current) {
    if (candidate.getVersion() == null) {
      return false;
    }
    if (current.getVersion() == null) {
      return true;
    }
    return candidate.getVersion() > current.getVersion();
  }

  /**
   * 聚合列表场景码：draft → published → archived 顺序取首个非空 scenarioCode。
   *
   * @param draft          草稿行
   * @param published      已发布行
   * @param latestArchived 最新归档行
   * @return 场景码；均无则 null
   */
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
}
