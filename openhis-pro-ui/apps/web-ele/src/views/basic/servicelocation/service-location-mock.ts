import type {
  ServiceLocationInitResponse,
  ServiceLocationItem,
} from '#/api/basic/service-location';
import {
  SERVICE_LOCATION_HERBAL_CATEGORY_CODE,
  SERVICE_LOCATION_ITEM_TABLE_DEVICE,
  SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
} from '#/api/basic/service-location';
import type { ApiOptionItem } from '#/types/api-response';

/** 开发阶段使用 mock，联调时改为 false */
export const USE_SERVICE_LOCATION_MOCK = false;

export interface MockOrgTreeNode {
  id: string;
  name: string;
  children?: MockOrgTreeNode[];
}

const MOCK_ORG_TREE: MockOrgTreeNode[] = [
  {
    id: 'org-root-outpatient',
    name: '门诊部',
    children: [
      { id: '1001', name: '内科门诊' },
      { id: '1002', name: '外科门诊' },
      { id: '1003', name: '儿科门诊' },
    ],
  },
  {
    id: 'org-root-inpatient',
    name: '住院部',
    children: [
      { id: '2001', name: '骨科病区' },
      { id: '2002', name: '儿科病区' },
      { id: '2003', name: '心内科病区' },
    ],
  },
  {
    id: 'org-root-emergency',
    name: '急诊科',
    children: [{ id: '3001', name: '急诊抢救室' }],
  },
];

const MOCK_ITEM_TABLE_OPTIONS: ApiOptionItem[] = [
  { label: '药品', value: SERVICE_LOCATION_ITEM_TABLE_MEDICATION },
  { label: '耗材', value: SERVICE_LOCATION_ITEM_TABLE_DEVICE },
];

const MOCK_MEDICATION_CATEGORY_OPTIONS: ApiOptionItem[] = [
  { label: '西药', value: '2' },
  { label: '中成药', value: '1' },
  { label: '中草药', value: SERVICE_LOCATION_HERBAL_CATEGORY_CODE },
];

const MOCK_DEVICE_CATEGORY_OPTIONS: ApiOptionItem[] = [
  { label: '卫生材料', value: 'sanitary_material' },
];

const MOCK_DEF_LOCATION_OPTIONS: ApiOptionItem[] = [
  { label: '门诊西药房', value: 'loc-outpatient-western' },
  { label: '门诊中药房', value: 'loc-outpatient-chinese' },
  { label: '住院中心药房', value: 'loc-inpatient-central' },
  { label: '急诊药房', value: 'loc-emergency' },
  { label: '器材库', value: 'loc-device-store' },
];

const MOCK_WESTERN_METHOD_OPTIONS: ApiOptionItem[] = [
  { label: '口服', value: 'oral' },
  { label: '静脉注射', value: 'intravenous' },
  { label: '肌肉注射', value: 'intramuscular' },
];

const MOCK_TCM_METHOD_OPTIONS: ApiOptionItem[] = [
  { label: '水煎服', value: 'decoction' },
  { label: '冲服', value: 'infusion' },
  { label: '外用', value: 'external' },
];

const MOCK_ITEM_DEFINITIONS: Record<string, ApiOptionItem[]> = {
  [`${SERVICE_LOCATION_ITEM_TABLE_MEDICATION}|2`]: [
    { label: '阿莫西林胶囊 0.25g', value: 'med-10001' },
    { label: '布洛芬缓释胶囊 0.3g', value: 'med-10002' },
  ],
  [`${SERVICE_LOCATION_ITEM_TABLE_MEDICATION}|${SERVICE_LOCATION_HERBAL_CATEGORY_CODE}`]: [
    { label: '黄芪饮片', value: 'med-20001' },
  ],
  [`${SERVICE_LOCATION_ITEM_TABLE_DEVICE}|sanitary_material`]: [
    { label: '一次性注射器 5ml', value: 'dev-30001' },
    { label: '医用纱布块', value: 'dev-30002' },
  ],
};

/** 各开立科室下的取药规则（模拟整科配置集） */
const MOCK_RULES_BY_ORG: Record<string, ServiceLocationItem[]> = {
  '1001': [
    {
      id: 'slt-1001-1',
      organizationId: '1001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      defLocationId: 'loc-outpatient-western',
      priority: 1,
      displayOrder: 1,
      remark: '门诊西药默认',
    },
    {
      id: 'slt-1001-2',
      organizationId: '1001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: SERVICE_LOCATION_HERBAL_CATEGORY_CODE,
      defLocationId: 'loc-outpatient-chinese',
      priority: 1,
      displayOrder: 2,
    },
    {
      id: 'slt-1001-3',
      organizationId: '1001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      defLocationId: 'loc-emergency',
      methodCode: 'intravenous',
      startTime: '22:00:00',
      endTime: '23:59:59',
      priority: 2,
      displayOrder: 3,
      remark: '夜间静脉用药走急诊药房（示例）',
    },
    {
      id: 'slt-1001-4',
      organizationId: '1001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      itemDefinitionId: 'med-10001',
      defLocationId: 'loc-outpatient-western',
      priority: 1,
      displayOrder: 4,
      remark: '阿莫西林单独走门诊西药房',
    },
    {
      id: 'slt-1001-5',
      organizationId: '1001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      itemDefinitionId: 'med-10002',
      defLocationId: 'loc-emergency',
      priority: 1,
      displayOrder: 5,
      remark: '布洛芬单独走急诊药房',
    },
  ],
  '1002': [
    {
      id: 'slt-1002-1',
      organizationId: '1002',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      defLocationId: 'loc-outpatient-western',
      priority: 1,
      displayOrder: 1,
    },
    {
      id: 'slt-1002-2',
      organizationId: '1002',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_DEVICE,
      distributionCategoryCode: 'sanitary_material',
      defLocationId: 'loc-device-store',
      priority: 1,
      displayOrder: 2,
    },
  ],
  '2001': [
    {
      id: 'slt-2001-1',
      organizationId: '2001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      defLocationId: 'loc-inpatient-central',
      priority: 1,
      displayOrder: 1,
      remark: '住院西药',
    },
  ],
  '3001': [
    {
      id: 'slt-3001-1',
      organizationId: '3001',
      itemTable: SERVICE_LOCATION_ITEM_TABLE_MEDICATION,
      distributionCategoryCode: '2',
      defLocationId: 'loc-emergency',
      priority: 1,
      displayOrder: 1,
    },
  ],
};

function cloneRules(rows: ServiceLocationItem[]): ServiceLocationItem[] {
  return rows.map((row) => ({ ...row }));
}

/** 模拟 init 下拉与科室树 */
export function getMockInitData(): ServiceLocationInitResponse & {
  organizationTree: MockOrgTreeNode[];
} {
  return {
    organizationTree: MOCK_ORG_TREE,
    itemTableOptions: MOCK_ITEM_TABLE_OPTIONS,
    medicationCategoryOptions: MOCK_MEDICATION_CATEGORY_OPTIONS,
    deviceCategoryOptions: MOCK_DEVICE_CATEGORY_OPTIONS,
    defLocationOptions: MOCK_DEF_LOCATION_OPTIONS,
    methodCodeOptions: MOCK_WESTERN_METHOD_OPTIONS,
    tcmMethodCodeOptions: MOCK_TCM_METHOD_OPTIONS,
  };
}

/** 按科室读取 mock 规则列表 */
export function getMockRulesByOrganization(organizationId: string): ServiceLocationItem[] {
  return cloneRules(MOCK_RULES_BY_ORG[organizationId] ?? []);
}

/** 整科覆盖保存到 mock 内存 */
export function saveMockRulesByOrganization(
  organizationId: string,
  rows: ServiceLocationItem[],
): void {
  MOCK_RULES_BY_ORG[organizationId] = cloneRules(rows);
}

/** 按物品类型与发放类别读取具体物品候选 */
export function getMockItemDefinitions(
  itemTable: string,
  distributionCategoryCode?: string,
): ApiOptionItem[] {
  const key = `${itemTable}|${distributionCategoryCode ?? ''}`;
  if (MOCK_ITEM_DEFINITIONS[key]) {
    return MOCK_ITEM_DEFINITIONS[key];
  }
  const prefix = `${itemTable}|`;
  return Object.entries(MOCK_ITEM_DEFINITIONS)
    .filter(([k]) => k.startsWith(prefix))
    .flatMap(([, items]) => items);
}

/** 在树中查找科室名称 */
export function findMockOrgName(
  nodes: MockOrgTreeNode[],
  organizationId: string,
): string | undefined {
  for (const node of nodes) {
    if (node.id === organizationId) {
      return node.name;
    }
    if (node.children?.length) {
      const childName = findMockOrgName(node.children, organizationId);
      if (childName) {
        return childName;
      }
    }
  }
  return undefined;
}
