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

  private boolean isHigherVersion(PrtTemplate candidate, PrtTemplate current) {
    if (candidate.getVersion() == null) {
      return false;
    }
    if (current.getVersion() == null) {
      return true;
    }
    return candidate.getVersion() > current.getVersion();
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
}
