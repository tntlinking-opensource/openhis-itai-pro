import type {
  ListDoctorStationOrderTemplateParams,
  OrderTemplateBindingGroup,
  OrderTemplateHeaderItem,
  OrderTemplateLineRowItem,
} from '#/api/basic/order-template';

import {
  listDoctorStationOrderTemplatesApi,
  ORDER_TEMPLATE_KIND_GENERAL,
  ORDER_TEMPLATE_SOURCE_FROM_ADVICE,
  ORDER_TEMPLATE_SOURCE_MAINTAINED,
} from '#/api/basic/order-template';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 绑定范围（对齐后端 OrderTemplateBinding） */
export type OrderTemplateBindingScope = 'AGREEMENT' | 'DEPARTMENT' | 'HOSPITAL' | 'PERSONAL';

/** 侧栏树节点 */
export interface OrderTemplateTreeNode {
  bindingScope?: OrderTemplateBindingScope;
  children?: OrderTemplateTreeNode[];
  header?: OrderTemplateHeaderItem;
  id: string;
  label: string;
  nodeType: 'group' | 'template';
  templateId?: string;
  tooltip?: string;
}

/** 侧栏选中组套（预览条展示） */
export interface DoctorStationOrderTemplateSelection {
  header: OrderTemplateHeaderItem;
  lineCount: number;
  lineSummary: string;
  lines: OrderTemplateLineRowItem[];
}

/** 绑定维度分组元数据（对齐 SDD agreement → personal → department → hospital） */
export const ORDER_TEMPLATE_BINDING_GROUP_META: Array<{
  bindingScope: OrderTemplateBindingScope;
  label: string;
  listKey: keyof OrderTemplateBindingGroup;
}> = [
  { bindingScope: 'AGREEMENT', label: '协定处方', listKey: 'agreementList' },
  { bindingScope: 'PERSONAL', label: '个人组套', listKey: 'personalList' },
  { bindingScope: 'DEPARTMENT', label: '科室组套', listKey: 'departmentList' },
  { bindingScope: 'HOSPITAL', label: '全院组套', listKey: 'hospitalList' },
];

/**
 * 医生站开立医嘱侧栏不展示的绑定范围。
 * 协定处方需在侧栏可见并可套用，故默认不排除任何范围。
 */
export const DOCTOR_STATION_EXCLUDED_BINDING_SCOPES: OrderTemplateBindingScope[] = [];

/** 明细行摘要（预览条/Tooltip） */
export function buildOrderTemplateLineSummary(lines: OrderTemplateLineRowItem[]): string {
  const names = lines.map((line) => String(line.productName ?? '').trim()).filter(Boolean);
  if (names.length === 0) {
    return '暂无明细';
  }
  if (names.length <= 4) {
    return names.join('、');
  }
  return `${names.slice(0, 4).join('、')} 等${names.length}项`;
}

/** 组套明细药品名全文（侧栏 hover Tooltip，不截断） */
export function buildOrderTemplateProductNamesTooltip(
  lines: OrderTemplateLineRowItem[] | undefined,
): string {
  const names = (lines ?? []).map((line) => String(line.productName ?? '').trim()).filter(Boolean);
  if (names.length === 0) {
    return '暂无药品';
  }
  return names.join('、');
}

/** 组套头 Tooltip 文案 */
export function resolveOrderTemplateHeaderTooltip(header: OrderTemplateHeaderItem): string {
  const desc =
    String(header.description ?? '').trim() || String(header.remark ?? '').trim() || '暂无说明';
  const source = String(header.templateSourceEnum ?? '')
    .trim()
    .toUpperCase();
  if (source === ORDER_TEMPLATE_SOURCE_FROM_ADVICE) {
    return `医嘱模板 · ${desc}`;
  }
  return desc;
}

/** 侧栏树节点展示名（另存模板加后缀便于区分） */
export function resolveOrderTemplateTreeLabel(header: OrderTemplateHeaderItem): string {
  const name = String(header.name ?? '').trim() || '未命名组套';
  const source = String(header.templateSourceEnum ?? '')
    .trim()
    .toUpperCase();
  if (source === ORDER_TEMPLATE_SOURCE_FROM_ADVICE) {
    return `${name}（模板）`;
  }
  return name;
}

/** 后端分段列表 → ElTree 数据 */
export function buildOrderTemplateTreeFromGroups(
  groups: OrderTemplateBindingGroup | undefined,
  options?: { excludeBindingScopes?: OrderTemplateBindingScope[] },
): OrderTemplateTreeNode[] {
  const excludedScopes = new Set(options?.excludeBindingScopes);
  const groupMeta = ORDER_TEMPLATE_BINDING_GROUP_META.filter(
    (group) => !excludedScopes.has(group.bindingScope),
  );
  return groupMeta.map((group) => {
    const headers = groups?.[group.listKey] ?? [];
    const children = headers.map<OrderTemplateTreeNode>((header) => {
      const templateId = preserveApiIdForRequest(header.orderTemplateId);
      return {
        id: `template-${templateId}`,
        label: resolveOrderTemplateTreeLabel(header),
        nodeType: 'template',
        templateId,
        bindingScope: group.bindingScope,
        header,
        tooltip: resolveOrderTemplateHeaderTooltip(header),
      };
    });
    return {
      id: `group-${group.bindingScope}`,
      label: group.label,
      nodeType: 'group',
      bindingScope: group.bindingScope,
      children,
    };
  });
}

/** 拉取医生站可套用组套列表 */
export async function fetchDoctorStationOrderTemplateGroups(
  params: Pick<
    ListDoctorStationOrderTemplateParams,
    'encounterId' | 'patientId' | 'searchKey' | 'templateKindEnum' | 'templateSourceEnum'
  >,
): Promise<OrderTemplateBindingGroup | undefined> {
  const encounterId = preserveApiIdForRequest(params.encounterId);
  const patientId = preserveApiIdForRequest(params.patientId);
  if (!encounterId || !patientId) {
    return undefined;
  }
  const res = await listDoctorStationOrderTemplatesApi({
    encounterId,
    patientId,
    templateSourceEnum: params.templateSourceEnum || ORDER_TEMPLATE_SOURCE_MAINTAINED,
    templateKindEnum: params.templateKindEnum || ORDER_TEMPLATE_KIND_GENERAL,
    searchKey: params.searchKey?.trim() || undefined,
  });
  return res?.groups;
}

/** 合并维护组套与另存医嘱模板（个人/科室/全院分段拼接） */
export function mergeDoctorStationOrderTemplateGroups(
  maintained: OrderTemplateBindingGroup | undefined,
  fromAdvice: OrderTemplateBindingGroup | undefined,
): OrderTemplateBindingGroup | undefined {
  if (!maintained && !fromAdvice) {
    return undefined;
  }
  const mergeLists = (
    left: OrderTemplateHeaderItem[] | undefined,
    right: OrderTemplateHeaderItem[] | undefined,
  ) => [...(left ?? []), ...(right ?? [])];
  return {
    agreementList: mergeLists(maintained?.agreementList, fromAdvice?.agreementList),
    personalList: mergeLists(maintained?.personalList, fromAdvice?.personalList),
    departmentList: mergeLists(maintained?.departmentList, fromAdvice?.departmentList),
    hospitalList: mergeLists(maintained?.hospitalList, fromAdvice?.hospitalList),
  };
}

/** 并行拉取 MAINTAINED + FROM_ADVICE，合并为侧栏统一列表 */
export async function fetchMergedDoctorStationOrderTemplateGroups(
  params: Pick<
    ListDoctorStationOrderTemplateParams,
    'encounterId' | 'patientId' | 'searchKey' | 'templateKindEnum'
  >,
): Promise<OrderTemplateBindingGroup | undefined> {
  const shared = {
    encounterId: params.encounterId,
    patientId: params.patientId,
    templateKindEnum: params.templateKindEnum || ORDER_TEMPLATE_KIND_GENERAL,
    searchKey: params.searchKey?.trim() || undefined,
  };
  const [maintained, fromAdvice] = await Promise.all([
    fetchDoctorStationOrderTemplateGroups({
      ...shared,
      templateSourceEnum: ORDER_TEMPLATE_SOURCE_MAINTAINED,
    }),
    fetchDoctorStationOrderTemplateGroups({
      ...shared,
      templateSourceEnum: ORDER_TEMPLATE_SOURCE_FROM_ADVICE,
    }),
  ]);
  return mergeDoctorStationOrderTemplateGroups(maintained, fromAdvice);
}

/** 在分段列表中按 id 查找组套头 */
export function findOrderTemplateHeaderInGroups(
  groups: OrderTemplateBindingGroup | undefined,
  templateId: string,
): OrderTemplateHeaderItem | undefined {
  const id = preserveApiIdForRequest(templateId);
  if (!id || !groups) {
    return undefined;
  }
  for (const meta of ORDER_TEMPLATE_BINDING_GROUP_META) {
    const hit = (groups[meta.listKey] ?? []).find(
      (item) => preserveApiIdForRequest(item.orderTemplateId) === id,
    );
    if (hit) {
      return hit;
    }
  }
  return undefined;
}
