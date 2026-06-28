import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';
import type { ApiOptionItem } from '#/types/api-response';

import { ORDER_TEMPLATE_SOURCE_FROM_ADVICE } from '#/api/basic/order-template';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 组套维护左侧树节点 */
export interface OrderTemplateMaintenanceTreeNode {
  bindingEnum?: string;
  children?: OrderTemplateMaintenanceTreeNode[];
  header?: OrderTemplateHeaderItem;
  id: string;
  label: string;
  nodeType: 'group' | 'template';
}

/** 组套树叶子展示名（含状态；另存模板加后缀） */
export function formatOrderTemplateTreeLabel(header: OrderTemplateHeaderItem): string {
  const name = String(header.name ?? '').trim() || '未命名组套';
  const status = String(header.statusEnumText ?? header.statusEnum ?? '').trim();
  const source = String(header.templateSourceEnum ?? '')
    .trim()
    .toUpperCase();
  let label = name;
  if (source === ORDER_TEMPLATE_SOURCE_FROM_ADVICE) {
    label = `${name}（模板）`;
  }
  if (!status) {
    return label;
  }
  return `${label}（${status}）`;
}

/** 维护页头列表 → 按绑定类型分组的 ElTree 数据 */
export function buildOrderTemplateMaintenanceTree(params: {
  bindingOptions: ApiOptionItem[];
  excludeBindingEnums?: string[];
  headers: OrderTemplateHeaderItem[];
  searchKeyword?: string;
}): OrderTemplateMaintenanceTreeNode[] {
  const keyword = String(params.searchKeyword ?? '')
    .trim()
    .toLowerCase();
  const excludedBindings = new Set(
    (params.excludeBindingEnums ?? []).map((item) => String(item).trim()).filter(Boolean),
  );
  const matchHeader = (header: OrderTemplateHeaderItem) => {
    if (!keyword) {
      return true;
    }
    const name = String(header.name ?? '').toLowerCase();
    const code = String(header.templateCode ?? '').toLowerCase();
    return name.includes(keyword) || code.includes(keyword);
  };

  return (params.bindingOptions ?? [])
    .filter((option) => !excludedBindings.has(String(option.value ?? '').trim()))
    .map((option) => {
      const bindingEnum = String(option.value ?? '').trim();
      const children = params.headers
        .filter((header) => String(header.bindingEnum ?? '').trim() === bindingEnum)
        .filter(matchHeader)
        .map<OrderTemplateMaintenanceTreeNode>((header) => {
          const templateId = preserveApiIdForRequest(header.orderTemplateId) ?? '';
          return {
            id: `template-${templateId}`,
            label: formatOrderTemplateTreeLabel(header),
            nodeType: 'template',
            header,
          };
        });
      return {
        id: `group-${bindingEnum}`,
        label: String(option.label ?? bindingEnum),
        nodeType: 'group',
        bindingEnum,
        children,
      } satisfies OrderTemplateMaintenanceTreeNode;
    })
    .filter((group) => !keyword || (group.children?.length ?? 0) > 0);
}

/** 在树数据中查找指定组套头 */
export function findOrderTemplateHeaderInTree(
  nodes: OrderTemplateMaintenanceTreeNode[],
  templateId: string,
): OrderTemplateHeaderItem | undefined {
  const id = preserveApiIdForRequest(templateId);
  if (!id) {
    return undefined;
  }
  for (const node of nodes) {
    if (node.nodeType === 'template') {
      const headerId = preserveApiIdForRequest(node.header?.orderTemplateId);
      if (headerId === id) {
        return node.header;
      }
      continue;
    }
    const hit = findOrderTemplateHeaderInTree(node.children ?? [], id);
    if (hit) {
      return hit;
    }
  }
  return undefined;
}
