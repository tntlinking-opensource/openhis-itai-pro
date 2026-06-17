<script lang="ts" setup>
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { ServiceLocationItem } from '#/api/basic/service-location';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { CopyDocument, Delete, DocumentCopy, Plus } from '@element-plus/icons-vue';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
  ElTimePicker,
  ElTree,
} from 'element-plus';

import {
  initServiceLocationApi,
  isMedicationItemTable,
  listServiceLocationByOrganizationApi,
  normalizeServiceLocationOptionItems,
  resolveCategoryOptionsForItemTable,
  resolveMethodOptionsForRule,
  saveServiceLocationConfigApi,
} from '#/api/basic/service-location';

import AddItemRuleDialog, { type AddItemRuleConfirmPayload } from './add-item-rule-dialog.vue';
import {
  USE_SERVICE_LOCATION_MOCK,
  getMockInitData,
  getMockRulesByOrganization,
  saveMockRulesByOrganization,
  type MockOrgTreeNode,
} from './service-location-mock';

defineOptions({ name: 'BasicServiceLocation' });

/** 规则行本地草稿主键（未落库行） */
interface DraftRow extends ServiceLocationItem {
  _clientKey: string;
}

/** 跨科室复制时暂存的规则模板（不含科室与主键） */
interface CopiedRuleTemplate {
  itemTable?: string;
  distributionCategoryCode?: string;
  itemDefinitionId?: string;
  itemDefinitionName?: string;
  methodCode?: string;
  defLocationId?: string;
  startTime?: string;
  endTime?: string;
}

const orgTree = ref<MockOrgTreeNode[]>([]);
const currentOrgId = ref('');
const currentOrgName = ref('');
const treeLoading = ref(false);

const savedSnapshot = ref('');
const draftRows = ref<DraftRow[]>([]);
const loading = ref(false);
const addItemDialogVisible = ref(false);

const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);
const selectedRows = ref<DraftRow[]>([]);
const copiedRuleTemplates = ref<CopiedRuleTemplate[]>([]);
const copiedFromOrgName = ref('');

const itemTableOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const deviceCategoryOptions = ref<ApiOptionItem[]>([]);
const defLocationOptions = ref<ApiOptionItem[]>([]);
const westernMethodOptions = ref<ApiOptionItem[]>([]);
const tcmMethodOptions = ref<ApiOptionItem[]>([]);

const queryParams = ref({ pageNo: 1, pageSize: 20 });

/** 生效时段默认：全天 */
const DEFAULT_START_TIME = '00:00:00';
const DEFAULT_END_TIME = '23:59:59';

let clientKeySeed = 0;

const isDirty = computed(() => serializeRows(draftRows.value) !== savedSnapshot.value);

const hasCopiedRules = computed(() => copiedRuleTemplates.value.length > 0);

/** 当前科室已配置的具体物品 ID，供弹窗去重 */
const boundItemDefinitionIds = computed(() =>
  draftRows.value.filter((row) => row.itemDefinitionId).map((row) => String(row.itemDefinitionId)),
);

const tableColumns: TableColumn[] = [
  { type: 'selection', width: 48, align: 'center', fixed: 'left', headerAlign: 'center' },
  {
    prop: 'priority',
    label: '优先级',
    width: 64,
    align: 'center',
    headerAlign: 'center',
    slot: 'priority',
  },
  {
    prop: 'itemTable',
    label: '物品类型',
    width: '10%',
    align: 'center',
    headerAlign: 'center',
    slot: 'itemTable',
  },
  {
    prop: 'distributionCategoryCode',
    label: '发放类别',
    width: '10%',
    align: 'center',
    headerAlign: 'center',
    slot: 'distributionCategory',
  },
  {
    prop: 'itemDefinitionId',
    label: '具体物品',
    width: '14%',
    align: 'center',
    headerAlign: 'center',
    slot: 'itemDefinition',
  },
  {
    prop: 'methodCode',
    label: '给药途径',
    width: '9%',
    align: 'center',
    headerAlign: 'center',
    slot: 'methodCode',
  },
  {
    prop: 'defLocationId',
    label: '取药药房',
    width: '12%',
    align: 'center',
    headerAlign: 'center',
    slot: 'defLocation',
  },
  {
    prop: 'timeRange',
    label: '生效时段',
    width: '22%',
    align: 'center',
    headerAlign: 'center',
    slot: 'timeRange',
  },
  {
    label: '操作',
    width: 168,
    fixed: 'right',
    align: 'center',
    headerAlign: 'center',
    slot: 'actions',
  },
];

/** 生成行草稿唯一键 */
function nextClientKey() {
  return `draft:${++clientKeySeed}`;
}

/** 解析生效时段，双空时回落为全天默认 */
function resolveEffectiveTimeRange(startTime?: string, endTime?: string) {
  if (!startTime && !endTime) {
    return { startTime: DEFAULT_START_TIME, endTime: DEFAULT_END_TIME };
  }
  return {
    startTime: startTime ?? DEFAULT_START_TIME,
    endTime: endTime ?? DEFAULT_END_TIME,
  };
}

/** 判断是否为指定物品规则行 */
function isItemSpecificRule(row: ServiceLocationItem) {
  return Boolean(row.itemDefinitionId);
}

/** 取规则行展示用标签（类别 / 指定物品） */
function rowRuleLabel(row: ServiceLocationItem) {
  return isItemSpecificRule(row) ? '指定物品规则' : '类别规则';
}

/** 将规则行序列化用于脏检查 */
function serializeRows(rows: ServiceLocationItem[]) {
  return JSON.stringify(
    rows.map((row) => ({
      itemTable: row.itemTable ?? '',
      distributionCategoryCode: row.distributionCategoryCode ?? '',
      itemDefinitionId: row.itemDefinitionId ?? '',
      methodCode: row.methodCode ?? '',
      defLocationId: row.defLocationId ?? '',
      startTime: row.startTime ?? '',
      endTime: row.endTime ?? '',
      priority: row.priority ?? 0,
      displayOrder: row.displayOrder ?? 0,
    })),
  );
}

/** 生成规则条件唯一键，用于去重（含具体物品） */
function buildRuleConditionKey(row: ServiceLocationItem) {
  return [
    row.itemTable ?? '',
    row.distributionCategoryCode ?? '',
    row.itemDefinitionId ?? '',
    row.methodCode ?? '',
    row.startTime ?? '',
    row.endTime ?? '',
  ].join('|');
}

/** 优先级竞争组：物品表 + 发放类别 + 具体项（用法/时段在组内用 priority 区分） */
function buildPriorityGroupKey(row: ServiceLocationItem) {
  return [row.itemTable ?? '', row.distributionCategoryCode ?? '', row.itemDefinitionId ?? ''].join(
    '|',
  );
}

/** 计算行在竞争组内的组内优先级（按表格自上而下，从 1 起） */
function resolveGroupPriority(row: DraftRow, rows: DraftRow[] = draftRows.value) {
  if (!row.itemTable || !row.distributionCategoryCode) {
    return undefined;
  }
  const groupKey = buildPriorityGroupKey(row);
  let groupIndex = 0;
  for (const item of rows) {
    if (buildPriorityGroupKey(item) !== groupKey) {
      continue;
    }
    groupIndex += 1;
    if (item._clientKey === row._clientKey) {
      return groupIndex;
    }
  }
  return undefined;
}

/** 查找同竞争组内相邻可交换行 */
function findAdjacentRowInSameGroup(row: DraftRow, direction: -1 | 1) {
  const rows = draftRows.value;
  const index = rows.findIndex((item) => item._clientKey === row._clientKey);
  if (index < 0) {
    return undefined;
  }
  const groupKey = buildPriorityGroupKey(row);
  let cursor = index + direction;
  while (cursor >= 0 && cursor < rows.length) {
    const candidate = rows[cursor];
    if (candidate && buildPriorityGroupKey(candidate) === groupKey) {
      return candidate;
    }
    cursor += direction;
  }
  return undefined;
}

/** 校验 HH:mm:ss */
function parseTimeToSeconds(time: string | undefined): number | undefined {
  if (!time) {
    return undefined;
  }
  const match = /^(\d{2}):(\d{2}):(\d{2})$/.exec(time.trim());
  if (!match) {
    return undefined;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  const second = Number(match[3]);
  if (hour > 23 || minute > 59 || second > 59) {
    return undefined;
  }
  return hour * 3600 + minute * 60 + second;
}

/** 按物品表取对应发放类别下拉 */
function categoryOptionsForItemTable(itemTable: string | undefined) {
  return resolveCategoryOptionsForItemTable(
    itemTable,
    medicationCategoryOptions.value,
    deviceCategoryOptions.value,
  );
}

/** 按物品表与发放类别取对应用法下拉 */
function methodOptionsForRow(row: ServiceLocationItem) {
  return resolveMethodOptionsForRule(
    row.itemTable,
    row.distributionCategoryCode,
    westernMethodOptions.value,
    tcmMethodOptions.value,
  );
}

/** 发放类别变更时清空已选用法（码表切换） */
function handleDistributionCategoryChange(row: DraftRow) {
  row.methodCode = undefined;
}

/** 物品类型变更时清空已选发放类别与用法 */
function handleItemTableChange(row: DraftRow) {
  row.distributionCategoryCode = undefined;
  row.methodCode = undefined;
}

/** 选项 value 转展示文案 */
function optionLabel(options: ApiOptionItem[], value: string | undefined) {
  if (!value) {
    return '-';
  }
  return options.find((item) => String(item.value) === String(value))?.label ?? value;
}

/** 将接口行转为草稿行（含类别默认与指定物品） */
function toDraftRows(rows: ServiceLocationItem[]): DraftRow[] {
  return rows.map((row) => {
    const timeRange = resolveEffectiveTimeRange(row.startTime, row.endTime);
    return {
      ...row,
      ...timeRange,
      _clientKey: row.id ? `persisted:${row.id}` : nextClientKey(),
    };
  });
}

/** 按竞争组聚拢行：组顺序保留首次出现位置，组内按已存 priority 升序 */
function sortRowsByPriorityGroup(rows: DraftRow[]) {
  const groupFirstIndex = new Map<string, number>();
  const groupMap = new Map<string, DraftRow[]>();

  rows.forEach((row, index) => {
    const key = buildPriorityGroupKey(row);
    if (!groupFirstIndex.has(key)) {
      groupFirstIndex.set(key, index);
      groupMap.set(key, []);
    }
    groupMap.get(key)!.push(row);
  });

  const orderedKeys = [...groupFirstIndex.entries()]
    .sort((left, right) => left[1] - right[1])
    .map(([key]) => key);

  const result: DraftRow[] = [];
  for (const key of orderedKeys) {
    const members = [...(groupMap.get(key) ?? [])];
    members.sort((left, right) => (left.priority ?? 0) - (right.priority ?? 0));
    result.push(...members);
  }
  return result;
}

/** 将选中行转为可跨科室粘贴的模板 */
function toCopiedTemplates(rows: DraftRow[]): CopiedRuleTemplate[] {
  return rows.map((row) => ({
    itemTable: row.itemTable,
    distributionCategoryCode: row.distributionCategoryCode,
    itemDefinitionId: row.itemDefinitionId,
    itemDefinitionName: row.itemDefinitionName,
    methodCode: row.methodCode,
    defLocationId: row.defLocationId,
    startTime: row.startTime,
    endTime: row.endTime,
  }));
}

/** 判断当前行是否可在竞争组内上移 */
function canMoveRowUp(row: DraftRow) {
  return Boolean(findAdjacentRowInSameGroup(row, -1));
}

/** 判断当前行是否可在竞争组内下移 */
function canMoveRowDown(row: DraftRow) {
  return Boolean(findAdjacentRowInSameGroup(row, 1));
}

/** 初始化下拉与科室树 */
async function initPage() {
  treeLoading.value = true;
  try {
    if (USE_SERVICE_LOCATION_MOCK) {
      const initData = getMockInitData();
      orgTree.value = initData.organizationTree;
      itemTableOptions.value = normalizeServiceLocationOptionItems(initData.itemTableOptions);
      medicationCategoryOptions.value = normalizeServiceLocationOptionItems(
        initData.medicationCategoryOptions,
      );
      deviceCategoryOptions.value = normalizeServiceLocationOptionItems(
        initData.deviceCategoryOptions,
      );
      defLocationOptions.value = normalizeServiceLocationOptionItems(initData.defLocationOptions);
      westernMethodOptions.value = normalizeServiceLocationOptionItems(initData.methodCodeOptions);
      tcmMethodOptions.value = normalizeServiceLocationOptionItems(initData.tcmMethodCodeOptions);
      return;
    }
    const initData = await initServiceLocationApi();
    orgTree.value =
      (initData.organizationOptions || []).map((item) => ({
        id: String(item.value),
        name: item.label,
        children:
          item.children?.map((child: ApiOptionItem) => ({
            id: String(child.value),
            name: child.label,
            children:
              (child as ApiOptionItem & { children?: ApiOptionItem[] }).children?.map(
                (grand: ApiOptionItem) => ({
                  id: String(grand.value),
                  name: grand.label,
                }),
              ) ?? [],
          })) ?? [],
      })) || [];
    itemTableOptions.value = normalizeServiceLocationOptionItems(initData.itemTableOptions);
    medicationCategoryOptions.value = normalizeServiceLocationOptionItems(
      initData.medicationCategoryOptions,
    );
    deviceCategoryOptions.value = normalizeServiceLocationOptionItems(
      initData.deviceCategoryOptions,
    );
    defLocationOptions.value = normalizeServiceLocationOptionItems(initData.defLocationOptions);
    westernMethodOptions.value = normalizeServiceLocationOptionItems(initData.methodCodeOptions);
    tcmMethodOptions.value = normalizeServiceLocationOptionItems(initData.tcmMethodCodeOptions);
  } finally {
    treeLoading.value = false;
  }
}

/** 清空表格勾选状态 */
async function clearSelection() {
  selectedRows.value = [];
  await nextTick();
  const gridApi = tableLayoutRef.value?.gridApi;
  if (gridApi) {
    const grid = gridApi.grid || gridApi;
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
  }
}

/** 加载当前科室全部规则 */
async function loadRulesForCurrentOrg() {
  if (!currentOrgId.value) {
    draftRows.value = [];
    savedSnapshot.value = serializeRows([]);
    await clearSelection();
    return;
  }
  loading.value = true;
  try {
    let rows: ServiceLocationItem[] = [];
    if (USE_SERVICE_LOCATION_MOCK) {
      rows = getMockRulesByOrganization(currentOrgId.value);
    } else {
      rows = await listServiceLocationByOrganizationApi({
        organizationId: currentOrgId.value,
      });
    }
    draftRows.value = toDraftRows(rows);
    savedSnapshot.value = serializeRows(draftRows.value);
    await clearSelection();
  } catch {
    draftRows.value = [];
    savedSnapshot.value = serializeRows([]);
    await clearSelection();
    ElMessage.error('加载取药科室配置失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

function isOrgTreeLeafNode(data: MockOrgTreeNode, treeNode: { isLeaf?: boolean }): boolean {
  if (treeNode.isLeaf === false) {
    return false;
  }
  const children = data.children;
  return !children || children.length === 0;
}

/** 选中叶子科室 */
async function handleOrgNodeClick(data: MockOrgTreeNode, treeNode: { isLeaf?: boolean }) {
  if (!isOrgTreeLeafNode(data, treeNode)) {
    return;
  }
  if (data.id === currentOrgId.value) {
    return;
  }
  currentOrgId.value = data.id;
  currentOrgName.value = data.name;
  await loadRulesForCurrentOrg();
}

/** 表格勾选变更 */
function handleSelectionChange(selection: DraftRow[] | Record<string, unknown>[]) {
  selectedRows.value = selection.filter(
    (row): row is DraftRow => typeof (row as DraftRow)._clientKey === 'string',
  );
}

/** 校验单行 */
function validateRow(row: ServiceLocationItem): boolean {
  const label = rowRuleLabel(row);
  if (!row.itemTable) {
    ElMessage.error(`${label}：物品类型不能为空`);
    return false;
  }
  if (!row.distributionCategoryCode) {
    ElMessage.error(`${label}：发放类别不能为空`);
    return false;
  }
  if (isItemSpecificRule(row) && !row.itemDefinitionId) {
    ElMessage.error(`${label}：具体物品不能为空`);
    return false;
  }
  if (!row.defLocationId) {
    ElMessage.error(`${label}：取药药房不能为空`);
    return false;
  }
  const methodOptions = methodOptionsForRow(row);
  if (row.methodCode) {
    const methodValid = methodOptions.some((item) => String(item.value) === String(row.methodCode));
    if (!methodValid) {
      ElMessage.error(`${label}：用法编码不在当前字典范围内`);
      return false;
    }
  }
  // 开始/结束均未填表示全天；只填一侧则校验不通过
  if (row.startTime || row.endTime) {
    if (!row.startTime || !row.endTime) {
      ElMessage.error(`${label}：生效时段须填写完整，或留空表示全天`);
      return false;
    }
    const startSeconds = parseTimeToSeconds(row.startTime);
    const endSeconds = parseTimeToSeconds(row.endTime);
    if (startSeconds === undefined || endSeconds === undefined) {
      ElMessage.error(`${label}：时间格式不正确`);
      return false;
    }
    if (startSeconds >= endSeconds) {
      ElMessage.error(`${label}：开始时间须早于结束时间`);
      return false;
    }
  }
  return true;
}

/** 校验条件组合不重复 */
function validateNoDuplicateRules(rows: ServiceLocationItem[]): boolean {
  const seen = new Set<string>();
  for (const row of rows) {
    const key = buildRuleConditionKey(row);
    if (seen.has(key)) {
      ElMessage.error('存在重复条件的规则，请检查');
      return false;
    }
    seen.add(key);
  }
  return true;
}

/** 按表格行序写入全局 priority/displayOrder（后端要求 1..N 连续）；组内优先级由行序推导 */
function normalizePriorities(rows: DraftRow[]) {
  rows.forEach((row, index) => {
    const next = index + 1;
    row.priority = next;
    row.displayOrder = next;
  });
}

/** 在竞争组内与相邻行交换位置，并同步全局 priority */
function handleMoveRow(row: DraftRow, direction: -1 | 1) {
  const rows = [...draftRows.value];
  const index = rows.findIndex((item) => item._clientKey === row._clientKey);
  if (index < 0) {
    return;
  }
  const neighbor = findAdjacentRowInSameGroup(row, direction);
  if (!neighbor) {
    return;
  }
  const neighborIndex = rows.findIndex((item) => item._clientKey === neighbor._clientKey);
  if (neighborIndex < 0) {
    return;
  }
  const current = rows[index];
  const adjacent = rows[neighborIndex];
  if (!current || !adjacent) {
    return;
  }
  rows[index] = adjacent;
  rows[neighborIndex] = current;
  draftRows.value = rows;
  normalizePriorities(draftRows.value);
}

/** 新增类别默认规则 */
function handleAddCategoryRule() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }
  draftRows.value.push({
    _clientKey: nextClientKey(),
    organizationId: currentOrgId.value,
    itemTable: undefined,
    distributionCategoryCode: undefined,
    defLocationId: undefined,
    startTime: DEFAULT_START_TIME,
    endTime: DEFAULT_END_TIME,
  });
  normalizePriorities(draftRows.value);
}

/** 打开指定物品弹窗 */
function handleOpenAddItemDialog() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }
  addItemDialogVisible.value = true;
}

/** 弹窗确认后追加指定物品规则行到主表 */
function handleAddItemRuleConfirm(payload: AddItemRuleConfirmPayload) {
  const existingKeys = new Set(draftRows.value.map((row) => buildRuleConditionKey(row)));
  let addedCount = 0;
  // 每个选中物品各生成一条草稿行，药房等字段在主表补全
  for (const item of payload.items) {
    const draft: DraftRow = {
      _clientKey: nextClientKey(),
      organizationId: currentOrgId.value,
      itemTable: payload.itemTable,
      distributionCategoryCode: payload.distributionCategoryCode,
      itemDefinitionId: String(item.value),
      itemDefinitionName: item.label,
      defLocationId: undefined,
      startTime: DEFAULT_START_TIME,
      endTime: DEFAULT_END_TIME,
    };
    const key = buildRuleConditionKey(draft);
    if (existingKeys.has(key)) {
      continue;
    }
    draftRows.value.push(draft);
    existingKeys.add(key);
    addedCount += 1;
  }
  if (!addedCount) {
    ElMessage.warning('所选物品均已在当前科室配置，未新增任何行');
    return;
  }
  normalizePriorities(draftRows.value);
  ElMessage.success(`已新增 ${addedCount} 条指定物品规则，请配置取药药房后保存`);
}

/** 复制选中规则到剪贴板，供其他科室粘贴 */
function handleCopySelected() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要复制的规则');
    return;
  }
  copiedRuleTemplates.value = toCopiedTemplates(selectedRows.value);
  copiedFromOrgName.value = currentOrgName.value;
  ElMessage.success(
    `已复制 ${copiedRuleTemplates.value.length} 条规则（来源：${currentOrgName.value}）`,
  );
}

/** 将剪贴板规则以新增方式粘贴到当前科室 */
function handlePaste() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择目标科室');
    return;
  }
  if (!copiedRuleTemplates.value.length) {
    ElMessage.warning('请先在其他科室勾选规则并点击「复制选中配置」');
    return;
  }
  const existingKeys = new Set(draftRows.value.map((row) => buildRuleConditionKey(row)));
  let addedCount = 0;
  let skippedCount = 0;
  // 逐条按新增方式写入当前科室草稿
  for (const template of copiedRuleTemplates.value) {
    const key = buildRuleConditionKey(template);
    if (existingKeys.has(key)) {
      skippedCount += 1;
      continue;
    }
    const timeRange = resolveEffectiveTimeRange(template.startTime, template.endTime);
    draftRows.value.push({
      _clientKey: nextClientKey(),
      organizationId: currentOrgId.value,
      itemTable: template.itemTable,
      distributionCategoryCode: template.distributionCategoryCode,
      itemDefinitionId: template.itemDefinitionId,
      itemDefinitionName: template.itemDefinitionName,
      methodCode: template.methodCode,
      defLocationId: template.defLocationId,
      startTime: timeRange.startTime,
      endTime: timeRange.endTime,
    });
    existingKeys.add(key);
    addedCount += 1;
  }
  if (!addedCount) {
    ElMessage.warning('粘贴完成：目标科室已存在相同条件规则，未新增任何行');
    return;
  }
  normalizePriorities(draftRows.value);
  const sourceHint = copiedFromOrgName.value ? `（来源：${copiedFromOrgName.value}）` : '';
  const skipHint = skippedCount ? `，跳过重复 ${skippedCount} 条` : '';
  ElMessage.success(`已粘贴新增 ${addedCount} 条规则${sourceHint}${skipHint}，请核对后保存`);
}

/** 批量删除勾选规则 */
async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要删除的规则');
    return;
  }
  const keys = new Set(selectedRows.value.map((row) => row._clientKey));
  draftRows.value = draftRows.value.filter((row) => !keys.has(row._clientKey));
  normalizePriorities(draftRows.value);
  await clearSelection();
  ElMessage.success('已删除选中规则，请保存配置');
}

/** 删除单行规则 */
function handleRemoveRow(row: DraftRow) {
  draftRows.value = draftRows.value.filter((item) => item._clientKey !== row._clientKey);
  normalizePriorities(draftRows.value);
}

/** 放弃未保存修改 */
async function handleDiscardChanges() {
  if (!isDirty.value) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认放弃当前科室的全部未保存修改吗？', '放弃修改', {
      type: 'warning',
    });
    await loadRulesForCurrentOrg();
  } catch {
    // 取消
  }
}

/** 整科覆盖保存 */
async function handleSaveAll() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择开立科室');
    return;
  }
  const draftSnapshot = [...draftRows.value];
  for (const row of draftSnapshot) {
    if (!validateRow(row)) {
      return;
    }
  }
  if (!validateNoDuplicateRules(draftSnapshot)) {
    return;
  }
  // 保存时按竞争组聚拢并重算全局 priority，编辑过程保持用户当前行序
  const rows = sortRowsByPriorityGroup(draftSnapshot);
  normalizePriorities(rows);
  loading.value = true;
  try {
    if (USE_SERVICE_LOCATION_MOCK) {
      await new Promise((resolve) => setTimeout(resolve, 300));
      saveMockRulesByOrganization(currentOrgId.value, rows);
    } else {
      await saveServiceLocationConfigApi({
        organizationId: currentOrgId.value,
        items: rows.map((row) => ({
          itemTable: row.itemTable ?? '',
          distributionCategoryCode: row.distributionCategoryCode ?? '',
          defLocationId: row.defLocationId ?? '',
          itemDefinitionId: row.itemDefinitionId,
          methodCode: row.methodCode,
          startTime: row.startTime,
          endTime: row.endTime,
          priority: row.priority ?? 1,
          displayOrder: row.displayOrder,
        })),
      });
    }
    await loadRulesForCurrentOrg();
    ElMessage.success('配置已保存（整科覆盖）');
  } catch {
    ElMessage.error('保存失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await initPage();
});

onActivated(() => {
  if (currentOrgId.value) {
    loadRulesForCurrentOrg();
  }
});
</script>

<template>
  <Page>
    <div class="flex h-full gap-3">
      <aside
        class="flex w-64 min-w-[220px] shrink-0 flex-col border-r border-[var(--el-border-color-light)] pr-2"
      >
        <div class="mb-2 font-semibold text-[var(--el-text-color-primary)]">开立科室</div>
        <div v-loading="treeLoading" class="min-h-0 flex-1 overflow-auto">
          <ElTree
            :data="orgTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleOrgNodeClick"
          />
        </div>
      </aside>

      <section class="slt-panel min-w-0 flex-1">
        <div class="slt-panel__head flex flex-wrap items-center justify-between gap-2">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold text-[var(--el-text-color-primary)]">
              {{ currentOrgName || '未选择科室' }}
            </span>
            <ElTag v-if="currentOrgId && isDirty" type="warning">未保存</ElTag>
            <ElTag v-else-if="currentOrgId" type="success">已同步</ElTag>
            <ElTag v-if="hasCopiedRules" type="info">
              已复制 {{ copiedRuleTemplates.length }} 条{{
                copiedFromOrgName ? `（${copiedFromOrgName}）` : ''
              }}
            </ElTag>
          </div>
          <ElButton :disabled="!currentOrgId || !isDirty" @click="handleDiscardChanges">
            放弃修改
          </ElButton>
        </div>

        <p class="slt-panel__desc m-0 text-sm text-[var(--el-text-color-regular)]">
          按发放类别配置取药药房；可通过「添加指定物品」为单个药品/耗材单独配置。可勾选规则后从其他科室复制，再切换到目标科室粘贴新增。优先级按「物品类型+发放类别+具体物品」分组，组内用法/时段不同的规则才竞争，数字越小越优先；指定物品规则优先于类别默认。
        </p>

        <TableLayout
          ref="tableLayoutRef"
          class="slt-table"
          :table-data="draftRows"
          :loading="loading"
          :total="draftRows.length"
          :query-params="queryParams"
          :form-items="[]"
          :table-columns="tableColumns"
          :show-top-query="false"
          :show-pagination="false"
          row-key="_clientKey"
          @selection-change="handleSelectionChange"
        >
          <template #toolbar-actions>
            <ElButton
              type="primary"
              :icon="Plus"
              :disabled="!currentOrgId"
              @click="handleAddCategoryRule"
            >
              添加新项目
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Plus"
              :disabled="!currentOrgId"
              :loading="loading"
              @click="handleSaveAll"
            >
              保存配置
            </ElButton>
            <ElButton
              plain
              :icon="CopyDocument"
              :disabled="!currentOrgId || !selectedRows.length"
              @click="handleCopySelected"
            >
              复制选中配置
            </ElButton>
            <ElButton
              plain
              :icon="DocumentCopy"
              :disabled="!currentOrgId || !hasCopiedRules"
              @click="handlePaste"
            >
              粘贴
            </ElButton>
            <ElButton
              type="danger"
              plain
              :icon="Delete"
              :disabled="!currentOrgId || !selectedRows.length"
              @click="handleBatchDelete"
            >
              批量删除
            </ElButton>
            <ElButton plain :icon="Plus" :disabled="!currentOrgId" @click="handleOpenAddItemDialog">
              添加指定物品
            </ElButton>
          </template>

          <template #priority="{ row }">
            <span class="slt-priority-badge">{{ resolveGroupPriority(row) ?? '-' }}</span>
          </template>

          <template #itemTable="{ row }">
            <span v-if="isItemSpecificRule(row)" class="slt-readonly-cell">
              {{ optionLabel(itemTableOptions, row.itemTable) }}
            </span>
            <ElSelect
              v-else
              v-model="row.itemTable"
              placeholder="必选"
              class="cell-control"
              @change="handleItemTableChange(row)"
            >
              <ElOption
                v-for="item in itemTableOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #distributionCategory="{ row }">
            <span v-if="isItemSpecificRule(row)" class="slt-readonly-cell">
              {{
                optionLabel(
                  categoryOptionsForItemTable(row.itemTable),
                  row.distributionCategoryCode,
                )
              }}
            </span>
            <ElSelect
              v-else
              :key="`${row._clientKey}-${row.itemTable ?? ''}`"
              v-model="row.distributionCategoryCode"
              :disabled="!row.itemTable"
              :placeholder="row.itemTable ? '必选' : '请先选择物品类型'"
              class="cell-control"
              @change="handleDistributionCategoryChange(row)"
            >
              <ElOption
                v-for="item in categoryOptionsForItemTable(row.itemTable)"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #itemDefinition="{ row }">
            <span v-if="isItemSpecificRule(row)" class="slt-readonly-cell">
              {{ row.itemDefinitionName || row.itemDefinitionId || '-' }}
            </span>
            <span v-else class="slt-readonly-cell slt-readonly-cell--muted">
              {{ isMedicationItemTable(row.itemTable) || row.itemTable ? '全部' : '-' }}
            </span>
          </template>

          <template #methodCode="{ row }">
            <ElSelect
              v-model="row.methodCode"
              clearable
              :disabled="!isMedicationItemTable(row.itemTable) || !row.distributionCategoryCode"
              :placeholder="
                !isMedicationItemTable(row.itemTable)
                  ? '耗材不限'
                  : row.distributionCategoryCode
                    ? '不限'
                    : '请先选发放类别'
              "
              class="cell-control"
            >
              <ElOption
                v-for="item in methodOptionsForRow(row)"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #defLocation="{ row }">
            <ElSelect
              v-model="row.defLocationId"
              filterable
              placeholder="取药药房"
              class="cell-control"
            >
              <ElOption
                v-for="item in defLocationOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #timeRange="{ row }">
            <div class="slt-time-row">
              <ElTimePicker
                v-model="row.startTime"
                placeholder="开始"
                clearable
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="slt-time-input"
              />
              <span class="slt-time-sep">~</span>
              <ElTimePicker
                v-model="row.endTime"
                placeholder="结束"
                clearable
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="slt-time-input"
              />
            </div>
          </template>

          <template #actions="{ row }">
            <div class="slt-row-actions">
              <ElButton
                link
                type="primary"
                :disabled="!canMoveRowUp(row)"
                @click="handleMoveRow(row, -1)"
              >
                上移
              </ElButton>
              <ElButton
                link
                type="primary"
                :disabled="!canMoveRowDown(row)"
                @click="handleMoveRow(row, 1)"
              >
                下移
              </ElButton>
              <ElButton link type="danger" @click="handleRemoveRow(row)">删除</ElButton>
            </div>
          </template>
        </TableLayout>
      </section>
    </div>

    <AddItemRuleDialog
      v-model:visible="addItemDialogVisible"
      :organization-id="currentOrgId"
      :item-table-options="itemTableOptions"
      :medication-category-options="medicationCategoryOptions"
      :device-category-options="deviceCategoryOptions"
      :bound-item-definition-ids="boundItemDefinitionIds"
      @confirm="handleAddItemRuleConfirm"
    />
  </Page>
</template>

<style scoped lang="scss">
.slt-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.slt-panel__head,
.slt-panel__desc {
  flex-shrink: 0;
}

.slt-table {
  flex: 1;
  min-width: 0;
  width: 100%;

  :deep(.vxe-grid),
  :deep(.vxe-table),
  :deep(.vxe-table--render-wrapper),
  :deep(.vxe-table--main-wrapper),
  :deep(.vxe-table--header),
  :deep(.vxe-table--body),
  :deep(.vxe-table--header-wrapper table),
  :deep(.vxe-table--body-wrapper table) {
    width: 100% !important;
  }

  :deep(.cell-control) {
    width: 100%;
  }

  :deep(.vxe-body--column),
  :deep(.vxe-header--column) {
    overflow: visible;
  }
}

.slt-priority-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.slt-row-actions {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 0 2px;
  white-space: nowrap;
}

.slt-readonly-cell {
  display: inline-block;
  width: 100%;
  padding: 0 4px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.slt-readonly-cell--muted {
  color: var(--el-text-color-regular);
}

.slt-time-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding-inline: 8px;
  box-sizing: border-box;
}

.slt-time-input {
  flex: 1;
  min-width: 0;
  max-width: 168px;
}

.slt-time-sep {
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}
</style>
