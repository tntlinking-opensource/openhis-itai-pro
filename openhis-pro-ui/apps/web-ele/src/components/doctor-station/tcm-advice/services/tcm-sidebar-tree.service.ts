import type { TcmHistoryTreeItem } from './tcm-prescription.service';

import type {
  OrderTemplateBindingGroup,
  OrderTemplateHeaderItem,
} from '#/api/basic/order-template';
import type { OrderTemplateTreeNode } from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import {
  buildOrderTemplateTreeFromGroups,
  DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
  resolveOrderTemplateHeaderTooltip,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

export type TcmSidebarTreeNodeType = 'group' | 'history' | 'template';

/** 中医 Tab 左侧树节点（组套 + 历史处方） */
export interface TcmSidebarTreeNode {
  children?: TcmSidebarTreeNode[];
  header?: OrderTemplateHeaderItem;
  herbSummary?: string;
  historyId?: string;
  id: string;
  label: string;
  nodeType: TcmSidebarTreeNodeType;
  templateId?: string;
  tooltip?: string;
}

/** 组套树节点 → 侧栏树节点 */
function mapTemplateTreeNodes(nodes: OrderTemplateTreeNode[]): TcmSidebarTreeNode[] {
  return nodes.map((node) => ({
    id: node.id,
    label: node.label,
    nodeType: node.nodeType,
    templateId: node.templateId,
    header: node.header,
    tooltip: node.tooltip,
    children: node.children ? mapTemplateTreeNodes(node.children) : undefined,
  }));
}

/** 关键字过滤组套分段（仅过滤模板叶子） */
function filterTemplateGroupsByKeyword(
  groups: OrderTemplateBindingGroup | undefined,
  keyword: string,
): OrderTemplateBindingGroup | undefined {
  if (!groups || !keyword) {
    return groups;
  }
  const lower = keyword.toLowerCase();
  const filterHeaders = (headers: OrderTemplateHeaderItem[] | undefined) =>
    (headers ?? []).filter((header) => {
      const name = String(header.name ?? '').toLowerCase();
      const code = String(header.templateCode ?? '').toLowerCase();
      return name.includes(lower) || code.includes(lower);
    });
  return {
    agreementList: filterHeaders(groups.agreementList),
    personalList: filterHeaders(groups.personalList),
    departmentList: filterHeaders(groups.departmentList),
    hospitalList: filterHeaders(groups.hospitalList),
  };
}

/** 组装中医 Tab 左侧树：个人/科室/全院组套 + 历史处方 */
export function buildTcmSidebarTree(params: {
  historyItems: TcmHistoryTreeItem[];
  searchKeyword?: string;
  templateGroups?: OrderTemplateBindingGroup;
}): TcmSidebarTreeNode[] {
  const keyword = String(params.searchKeyword ?? '')
    .trim()
    .toLowerCase();
  const filteredGroups = filterTemplateGroupsByKeyword(params.templateGroups, keyword);
  const templateSection = mapTemplateTreeNodes(
    buildOrderTemplateTreeFromGroups(filteredGroups, {
      excludeBindingScopes: DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
    }),
  ).filter((group) => (group.children?.length ?? 0) > 0 || !keyword);

  const historyChildren = params.historyItems
    .filter((item) => {
      if (!keyword) {
        return true;
      }
      return (
        item.prescriptionNo.toLowerCase().includes(keyword) ||
        item.summaryText.toLowerCase().includes(keyword) ||
        item.herbSummary.toLowerCase().includes(keyword)
      );
    })
    .map<TcmSidebarTreeNode>((item) => ({
      id: item.id,
      label: item.summaryText || item.prescriptionNo,
      nodeType: 'history',
      historyId: item.id,
      herbSummary: item.herbSummary,
      tooltip: item.herbSummary || item.summaryText || '暂无药品',
    }));

  const historySection: TcmSidebarTreeNode = {
    id: 'group-history',
    label: '历史处方',
    nodeType: 'group',
    children: historyChildren,
  };

  return [...templateSection, historySection];
}

/** 历史节点展示文案 */
export function formatTcmHistoryNodeTooltip(item: TcmHistoryTreeItem | undefined): string {
  if (!item) {
    return '暂无说明';
  }
  const parts = [item.summaryText, item.herbSummary].filter((text) => String(text ?? '').trim());
  return parts.join(' · ') || '暂无药品';
}

/** 组套节点 Tooltip */
export function resolveTcmTemplateNodeTooltip(header: OrderTemplateHeaderItem | undefined): string {
  if (!header) {
    return '暂无说明';
  }
  return resolveOrderTemplateHeaderTooltip(header);
}
