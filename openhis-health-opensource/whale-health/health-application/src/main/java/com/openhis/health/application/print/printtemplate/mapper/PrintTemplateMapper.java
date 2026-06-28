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

package com.openhis.health.application.print.printtemplate.mapper;

import com.openhis.health.application.print.printtemplate.dto.PrtTemplateDto;
import com.openhis.health.application.print.printtemplate.request.SaveDraftRequest;
import com.openhis.health.domain.print.printtemplate.command.PublishTemplateCommand;
import com.openhis.health.domain.print.printtemplate.command.SaveDraftCommand;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;

/**
 * 画布打印模板应用层装配：Request/实体 → Command/DTO。
 *
 * <p>禁止承载业务流程判断；PrintJson 结构化出参经 {@link com.openhis.health.application.print.printtemplate.support.PrintJsonSupport}。</p>
 */
public interface PrintTemplateMapper {

  /**
   * 保存草稿请求转领域命令。
   *
   * @param request 保存请求；调用方保证非 null
   * @param tenantId 当前租户
   * @param busNo 解析后的 busNo
   * @param printJson 已序列化的 PrintJson
   * @param samplePrintDataJson 已序列化的示例数据；可 null
   * @param existingDraftId 已有草稿主键；新建时为 null
   * @return 保存命令
   */
  SaveDraftCommand ConverterSaveDraftCommand(
      SaveDraftRequest request,
      Long tenantId,
      String busNo,
      String printJson,
      String samplePrintDataJson,
      Long existingDraftId);

  /**
   * 新建空白草稿命令。
   *
   * @param tenantId 当前租户
   * @param busNo 生成的 busNo
   * @param name 展示名称
   * @param printJson 空白版式 JSON
   * @return 保存命令
   */
  SaveDraftCommand ConverterBlankDraftCommand(
      Long tenantId, String busNo, String name, String printJson);

  /**
   * 发布命令。
   *
   * @param draftId 草稿主键
   * @param tenantId 当前租户
   * @param busNo 模板 busNo
   * @return 发布命令
   */
  PublishTemplateCommand ConverterPublishTemplateCommand(Long draftId, Long tenantId, String busNo);

  /**
   * 模板实体转详情 DTO（PrintJson 结构化出参）。
   *
   * @param template 模板行；null 时返回 null
   * @return 详情 DTO
   */
  PrtTemplateDto ConverterPrtTemplateDto(PrtTemplate template);
}
