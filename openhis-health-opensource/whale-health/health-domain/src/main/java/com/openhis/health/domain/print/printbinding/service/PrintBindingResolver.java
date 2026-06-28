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

package com.openhis.health.domain.print.printbinding.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.print.printbinding.repository.PrtPrintBindingRepository;
import com.openhis.health.domain.print.printtemplate.constants.PrtTenantScope;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.service.PrtTemplateManager;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务场景模板绑定解析与保存（PrintBindingResolver）。
 */
@Service
@RequiredArgsConstructor
public class PrintBindingResolver {

  private final PrtPrintBindingRepository prtPrintBindingRepository;
  private final PrtTemplateManager prtTemplateManager;

  /**
   * 解析 bindingCode → busNo（TC-PRT-BND-RES-001）。
   *
   * @param bindingCode 场景入口码
   * @param tenantId 租户
   * @return 有效 busNo
   */
  public String resolve(String bindingCode, Long tenantId) {
    String code = trimToNull(bindingCode);
    if (code == null || tenantId == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "绑定场景无效");
    }
  // Pre：租户 override。
    PrtPrintBinding tenantRow =
        prtPrintBindingRepository.findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
            tenantId, code, null);
    if (tenantRow != null && Boolean.TRUE.equals(tenantRow.getEnabled())) {
      return tenantRow.getBusNo();
    }
  // Pre：平台 seed fallback。
    PrtPrintBinding platformRow =
        prtPrintBindingRepository.findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
            PrtTenantScope.PLATFORM_TENANT_ID, code, null);
    if (platformRow != null && Boolean.TRUE.equals(platformRow.getEnabled())) {
      return platformRow.getBusNo();
    }
    throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到绑定场景：" + code);
  }

  /**
   * 平台出厂 binding 定义清单（TC-PRT-BND-LST-001）。
   *
   * @return 平台 seed 行
   */
  public List<PrtPrintBinding> listDefinitions() {
    return prtPrintBindingRepository.findByTenantIdAndIsDeletedFalse(PrtTenantScope.PLATFORM_TENANT_ID);
  }

  /**
   * 租户 override 绑定行（TC-PRT-BND-LST-002）。
   *
   * @param tenantId 租户
   * @return 租户绑定行（organization_id 为 NULL）
   */
  public List<PrtPrintBinding> listOverridesForTenant(Long tenantId) {
    if (tenantId == null) {
      return List.of();
    }
    return prtPrintBindingRepository.findByTenantIdAndOrganizationIdIsNullAndIsDeletedFalse(tenantId);
  }

  /**
   * 保存租户级 bindingCode→busNo 映射（TC-PRT-BND-SAVE-001）。
   *
   * @param cmd 全量绑定命令
   */
  @Transactional
  public void saveTenantBindings(SaveTenantBindingsCommand cmd) {
    if (cmd.getTenantId() == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == cmd.getTenantId()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "不可修改平台出厂绑定");
    }
    Map<String, String> bindings = cmd.getBindingCodeToBusNo();
    if (bindings == null || bindings.isEmpty()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "绑定配置不能为空");
    }

    for (Map.Entry<String, String> entry : bindings.entrySet()) {
      String bindingCode = trimToNull(entry.getKey());
      String busNo = trimToNull(entry.getValue());
      if (bindingCode == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "bindingCode 不能为空");
      }
      if (busNo == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
      }
    // Pre：目标 busNo 须存在 published 模板。
      PrtTemplate published = prtTemplateManager.findPublished(busNo, cmd.getTenantId());
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
      }

      PrtPrintBinding existing =
          prtPrintBindingRepository.findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
              cmd.getTenantId(), bindingCode, null);
      if (existing != null) {
        existing.setBusNo(busNo);
        existing.setEnabled(Boolean.TRUE);
        prtPrintBindingRepository.save(existing);
      } else {
        PrtPrintBinding row = new PrtPrintBinding();
        row.setTenantId(cmd.getTenantId());
        row.setOrganizationId(null);
        row.setBindingCode(bindingCode);
        row.setBusNo(busNo);
        row.setEnabled(Boolean.TRUE);
        prtPrintBindingRepository.save(row);
      }
    }
  }
}
