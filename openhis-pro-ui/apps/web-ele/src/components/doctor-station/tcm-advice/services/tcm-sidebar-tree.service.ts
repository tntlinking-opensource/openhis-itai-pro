import type { TcmHerbTooltipItem, TcmHistoryTreeItem } from './tcm-prescription.service';

import type {
  OrderTemplateBindingGroup,
  OrderTemplateHeaderItem,
} from '#/api/basic/order-template';
import type { OrderTemplateTreeNode } from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import {
  buildOrderTemplateTreeFromGroups,
  DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

export type TcmSidebarTreeNodeType = 'group' | 'history' | 'template';

/** 中医 Tab 左侧树节点（组套 + 历史处方） */
export interface TcmSidebarTreeNode {
  children?: TcmSidebarTreeNode[];
  /** 是否本次就诊历史方 */
  currentVisit?: boolean;
  header?: OrderTemplateHeaderItem;
  herbItems?: TcmHerbTooltipItem[];
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

/** 组装中医 Tab 左侧树：协定/个人/科室/全院组套 + 历史处方 */
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
        item.herbSummary.toLowerCase().includes(keyword) ||
        item.authoredByName.toLowerCase().includes(keyword) ||
        item.openedDateText.toLowerCase().includes(keyword) ||
        item.treeLabel.toLowerCase().includes(keyword)
      );
    })
    .map<TcmSidebarTreeNode>((item) => ({
      id: item.id,
      // 味数 · 开立人 · 日期（到天）
      label: item.treeLabel || item.summaryText || item.prescriptionNo,
      nodeType: 'history',
      historyId: item.id,
      currentVisit: item.currentVisit,
      herbItems: item.herbItems,
      herbSummary: item.herbSummary,
      // hover 展示药品名（非树节点摘要）
      tooltip: item.herbSummary || '暂无药品',
    }));

  const historySection: TcmSidebarTreeNode = {
    id: 'group-history',
    label: '历史处方',
    nodeType: 'group',
    children: historyChildren,
  };

  return [...templateSection, historySection];
}

/** 历史节点 Tooltip：药品名 */
export function formatTcmHistoryNodeTooltip(item: TcmHistoryTreeItem | undefined): string {
  if (!item) {
    return '暂无药品';
  }
  return String(item.herbSummary ?? '').trim() || '暂无药品';
}

/** 侧栏叶子 → Tooltip 结构化药材（历史有克数；组套仅药名） */
export function resolveTcmSidebarLeafHerbItems(
  data: TcmSidebarTreeNode,
  options?: { templateProductSummary?: string },
): TcmHerbTooltipItem[] {
  if (data.nodeType === 'history') {
    if (data.herbItems && data.herbItems.length > 0) {
      return data.herbItems;
    }
    const summary = String(data.herbSummary ?? data.tooltip ?? '').trim();
    if (!summary) {
      return [];
    }
    // 无结构化时按顿号拆药名
    return summary
      .split(/[，,]/)
      .map((name) => name.trim())
      .filter(Boolean)
      .map((name) => ({ name, doseText: '' }));
  }
  if (data.nodeType === 'template') {
    const cached = String(options?.templateProductSummary ?? data.tooltip ?? '').trim();
    if (!cached || cached === '加载中…' || cached === '暂无药品') {
      return [];
    }
    return cached
      .split(/[，,]/)
      .map((name) => name.trim())
      .filter(Boolean)
      .map((name) => ({ name, doseText: '' }));
  }
  return [];
}

/** 侧栏叶子节点 Tooltip：历史/组套均展示药品名 */
export function resolveTcmSidebarLeafTooltip(
  data: TcmSidebarTreeNode,
  options?: { templateProductSummary?: string },
): string {
  if (data.nodeType === 'history') {
    return String(data.herbSummary ?? data.tooltip ?? '').trim() || '暂无药品';
  }
  if (data.nodeType === 'template') {
    const cached = String(options?.templateProductSummary ?? '').trim();
    if (cached) {
      return cached;
    }
    return '加载中…';
  }
  return '';
}
