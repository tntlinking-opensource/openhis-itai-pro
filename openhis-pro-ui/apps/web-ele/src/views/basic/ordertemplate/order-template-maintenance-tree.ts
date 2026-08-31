import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';
import type { ApiOptionItem } from '#/types/api-response';

import {
  ORDER_TEMPLATE_BINDING_DEPARTMENT,
  ORDER_TEMPLATE_BINDING_PERSONAL,
  ORDER_TEMPLATE_SOURCE_FROM_ADVICE,
} from '#/api/basic/order-template';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 组套维护左侧树节点 */
export interface OrderTemplateMaintenanceTreeNode {
  bindingEnum?: string;
  children?: OrderTemplateMaintenanceTreeNode[];
  header?: OrderTemplateHeaderItem;
  id: string;
  label: string;
  /** group=绑定维度；owner=个人/科室下的归属对象；template=组套叶子 */
  nodeType: 'group' | 'owner' | 'template';
  objectId?: string;
  objectName?: string;
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

/** 是否按归属对象二次分组（个人/科室） */
function shouldGroupByOwner(bindingEnum: string): boolean {
  return (
    bindingEnum === ORDER_TEMPLATE_BINDING_PERSONAL ||
    bindingEnum === ORDER_TEMPLATE_BINDING_DEPARTMENT
  );
}

/** 归属分组展示名（无名称时回退） */
function formatOwnerGroupLabel(bindingEnum: string, objectId: string, objectName?: string): string {
  const name = String(objectName ?? '').trim();
  if (name) {
    return name;
  }
  if (bindingEnum === ORDER_TEMPLATE_BINDING_PERSONAL) {
    return objectId ? `未命名人员（${objectId}）` : '未指定人员';
  }
  if (bindingEnum === ORDER_TEMPLATE_BINDING_DEPARTMENT) {
    return objectId ? `未命名科室（${objectId}）` : '未指定科室';
  }
  return objectId || '未指定归属';
}

/** 将同一绑定下的头列表按 objectId 聚合成归属子节点 */
function buildOwnerGroupedChildren(
  bindingEnum: string,
  headers: OrderTemplateHeaderItem[],
): OrderTemplateMaintenanceTreeNode[] {
  // 保持首次出现顺序，避免每次刷新树抖动
  const ownerOrder: string[] = [];
  const headersByOwner = new Map<string, OrderTemplateHeaderItem[]>();
  for (const header of headers) {
    const objectId = preserveApiIdForRequest(header.objectId) ?? '';
    const key = objectId || '__empty__';
    if (!headersByOwner.has(key)) {
      headersByOwner.set(key, []);
      ownerOrder.push(key);
    }
    headersByOwner.get(key)?.push(header);
  }

  return ownerOrder.map((key) => {
    const ownerHeaders = headersByOwner.get(key) ?? [];
    const objectId = key === '__empty__' ? '' : key;
    const objectName = String(ownerHeaders[0]?.objectName ?? '').trim();
    const children = ownerHeaders.map<OrderTemplateMaintenanceTreeNode>((header) => {
      const templateId = preserveApiIdForRequest(header.orderTemplateId) ?? '';
      return {
        id: `template-${templateId}`,
        label: formatOrderTemplateTreeLabel(header),
        nodeType: 'template',
        header,
        bindingEnum,
        objectId: preserveApiIdForRequest(header.objectId) ?? undefined,
        objectName: header.objectName,
      };
    });
    return {
      id: `owner-${bindingEnum}-${objectId || 'empty'}`,
      label: formatOwnerGroupLabel(bindingEnum, objectId, objectName),
      nodeType: 'owner',
      bindingEnum,
      objectId: objectId || undefined,
      objectName: objectName || undefined,
      children,
    } satisfies OrderTemplateMaintenanceTreeNode;
  });
}

/** 维护页头列表 → 按绑定类型（个人/科室下再按归属）分组的 ElTree 数据 */
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
    const owner = String(header.objectName ?? '').toLowerCase();
    return name.includes(keyword) || code.includes(keyword) || owner.includes(keyword);
  };

  return (params.bindingOptions ?? [])
    .filter((option) => !excludedBindings.has(String(option.value ?? '').trim()))
    .map((option) => {
      const bindingEnum = String(option.value ?? '').trim();
      const matchedHeaders = params.headers
        .filter((header) => String(header.bindingEnum ?? '').trim() === bindingEnum)
        .filter(matchHeader);

      let children: OrderTemplateMaintenanceTreeNode[];
      if (shouldGroupByOwner(bindingEnum)) {
        children = buildOwnerGroupedChildren(bindingEnum, matchedHeaders);
      } else {
        children = matchedHeaders.map<OrderTemplateMaintenanceTreeNode>((header) => {
          const templateId = preserveApiIdForRequest(header.orderTemplateId) ?? '';
          return {
            id: `template-${templateId}`,
            label: formatOrderTemplateTreeLabel(header),
            nodeType: 'template',
            header,
            bindingEnum,
            objectId: preserveApiIdForRequest(header.objectId) ?? undefined,
            objectName: header.objectName,
          };
        });
      }

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
