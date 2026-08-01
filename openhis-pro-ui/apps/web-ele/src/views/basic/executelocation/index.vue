<script lang="ts" setup>
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { BatchAddConfirmPayload } from './batch-add-dialog.vue';

import type {
  CreateExecuteLocationParams,
  ExecuteLocationItem,
  PageActivityDefinitionParams,
  PageExecuteLocationParams,
  UpdateExecuteLocationParams,
} from '#/api/basic/execute-location';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  batchCreateExecuteLocationApi,
  createExecuteLocationApi,
  deleteExecuteLocationApi,
  initExecuteLocationApi,
  pageActivityDefinitionForExecuteLocationApi,
  pageExecuteLocationApi,
  updateExecuteLocationApi,
} from '#/api/basic/execute-location';
import { useRequest } from '#/hooks/use-request';

import BatchAddDialog from './batch-add-dialog.vue';

defineOptions({ name: 'BasicExecuteLocation' });

interface OrgTreeNode {
  id: string;
  name: string;
  children?: OrgTreeNode[];
}

interface ExecuteLocationOrgOption extends ApiOptionItem {
  children?: ExecuteLocationOrgOption[];
}

/** 表格行：未落库行用 _clientKey 作为稳定 row-key */
interface ExecuteLocationTableRow extends ExecuteLocationItem {
  _clientKey: string;
}

/** 跨科室复制时暂存的规则模板（不含开立科室与主键） */
interface CopiedRuleTemplate {
  activityCategoryCode?: string;
  activityDefinitionId?: string;
  activityDefinitionName?: string;
  executionOrgId?: string;
  executionLocationId?: string;
  startTime?: string;
  endTime?: string;
  displayOrder?: number;
}

/** 拉取全量规则时的分页大小（复制/粘贴去重均按全量，不限于当前页） */
const FETCH_ALL_PAGE_SIZE = 200;

const orgTree = ref<OrgTreeNode[]>([]);
const currentOrgId = ref<string>('');
const currentOrgName = ref('');
const treeLoading = ref(false);

const list = ref<ExecuteLocationTableRow[]>([]);
const loading = ref(false);
const total = ref(0);

const queryParams = reactive<PageExecuteLocationParams>({
  organizationId: '',
  pageNo: 1,
  pageSize: 20,
});

const activityCategoryOptions = ref<ApiOptionItem[]>([]);
/** 执行科室下拉（扁平，供行内与批量弹窗） */
const executionOrgOptions = ref<ApiOptionItem[]>([]);
const rowActivityDefinitionOptions = ref<Record<string, ApiOptionItem[]>>({});
const batchDialogVisible = ref(false);
const batchSaving = ref(false);
const copyLoading = ref(false);
const pasteLoading = ref(false);
/** 跨科室剪贴板：复制源科室的全部规则模板 */
const copiedRuleTemplates = ref<CopiedRuleTemplate[]>([]);
const copiedFromOrgId = ref('');
const copiedFromOrgName = ref('');
let clientKeySeed = 0;
let listRequestSeq = 0;

/** Long/主键字段转 string，空值返回 undefined */
function toOptionalIdString(value: null | number | string | undefined): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return String(value);
}

/** 项目 ID 转 string，空值返回空串 */
function toActivityDefinitionIdString(value: null | number | string | undefined): string {
  if (value === undefined || value === null) {
    return '';
  }
  return String(value);
}

/** 生成未落库行的本地主键 */
function nextClientKey() {
  return `draft:${++clientKeySeed}`;
}

/** 已落库主键：纯数字 Long 字符串 */
function isPersistedRowId(id: ExecuteLocationItem['id']): id is string {
  return id !== undefined && id !== null && /^\d+$/.test(String(id));
}

/** 规范化项目下拉 value/label，避免 ElSelect 因类型不一致无法回显名称 */
function normalizeActivityDefinitionOptions(options: ApiOptionItem[]): ApiOptionItem[] {
  return options.map((option) => ({
    label: String(option.label ?? option.value ?? ''),
    value: String(option.value ?? ''),
  }));
}

/** 开立科室树顶层节点 id（医院根），执行科室下拉须排除 */
function collectHospitalRootIds(nodes: ExecuteLocationOrgOption[]): Set<string> {
  const ids = new Set<string>();
  for (const node of nodes) {
    const value = String(node.value ?? '').trim();
    if (value) {
      ids.add(value);
    }
  }
  return ids;
}

/** 递归扁平化机构树为下拉项：仅叶子科室，排除医院等上级节点 */
function flattenOrgTree(
  nodes: ExecuteLocationOrgOption[],
  excludeIds?: Set<string>,
): ApiOptionItem[] {
  const seen = new Set<string>();
  const result: ApiOptionItem[] = [];
  const walk = (list: ExecuteLocationOrgOption[]) => {
    for (const node of list) {
      const children = node.children ?? [];
      // 有子节点的是医院/上级，不当作可选执行科室
      if (children.length > 0) {
        walk(children);
        continue;
      }
      const value = String(node.value ?? '').trim();
      const label = String(node.label ?? '').trim();
      if (!value || seen.has(value) || excludeIds?.has(value)) {
        continue;
      }
      seen.add(value);
      result.push({ value, label: label || value });
    }
  };
  walk(nodes);
  return result;
}

/** 将 init 机构树 Option 转为左侧 ElTree 节点 */
function mapOrgOptionsToTree(options: ExecuteLocationOrgOption[]): OrgTreeNode[] {
  return options.map((item) => ({
    id: String(item.value),
    name: item.label,
    children:
      item.children?.map((child) => ({
        id: String(child.value),
        name: child.label,
        children:
          child.children?.map((grand) => ({
            id: String(grand.value),
            name: grand.label,
            children: [],
          })) ?? [],
      })) ?? [],
  }));
}

function setRowActivityDefinitionOptions(row: ExecuteLocationTableRow, options: ApiOptionItem[]) {
  rowActivityDefinitionOptions.value = {
    ...rowActivityDefinitionOptions.value,
    [row._clientKey]: normalizeActivityDefinitionOptions(options),
  };
}

function clearRowActivityDefinitionOptions(row: ExecuteLocationTableRow) {
  const { [row._clientKey]: _removed, ...rest } = rowActivityDefinitionOptions.value;
  rowActivityDefinitionOptions.value = rest;
}

function getActivityDefinitionOptionsForRow(row: ExecuteLocationTableRow) {
  return rowActivityDefinitionOptions.value[row._clientKey] || [];
}

/** 用列表行自带的 activityDefinitionName 预填项目下拉 */
function hydrateRowActivityDefinitionOption(row: ExecuteLocationTableRow) {
  const defId = toActivityDefinitionIdString(row.activityDefinitionId);
  if (!defId) {
    return;
  }
  const label = row.activityDefinitionName?.trim() || defId;
  setRowActivityDefinitionOptions(row, [{ value: defId, label }]);
}

/** 将后端 LocalTime / 字符串统一为 HH:mm:ss */
function formatExecuteLocationTime(value: unknown): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value) && value.length >= 2) {
    const [hour, minute, second = 0] = value;
    return [hour, minute, second].map((part) => String(part).padStart(2, '0')).join(':');
  }
  if (typeof value === 'object') {
    const parts = value as { hour?: number; minute?: number; second?: number };
    if (typeof parts.hour === 'number' && typeof parts.minute === 'number') {
      const second = typeof parts.second === 'number' ? parts.second : 0;
      return [parts.hour, parts.minute, second]
        .map((part) => String(part).padStart(2, '0'))
        .join(':');
    }
  }
  return String(value);
}

/** 规范化列表行中的 Long / 时间 / 名称字段 */
function normalizeExecuteLocationRow(item: ExecuteLocationItem): ExecuteLocationItem {
  const activityDefinitionName =
    item.activityDefinitionName?.trim() ||
    (
      item as ExecuteLocationItem & { activityDefinitionId_dictText?: string }
    ).activityDefinitionId_dictText?.trim();

  return {
    ...item,
    id: toOptionalIdString(item.id),
    organizationId: toOptionalIdString(item.organizationId),
    activityDefinitionId: toOptionalIdString(item.activityDefinitionId),
    activityDefinitionName: activityDefinitionName || undefined,
    executionOrgId: toOptionalIdString(item.executionOrgId),
    executionLocationId: toOptionalIdString(item.executionLocationId),
    startTime: formatExecuteLocationTime(item.startTime),
    endTime: formatExecuteLocationTime(item.endTime),
  };
}

/** 接口行转为表格行（含稳定 row-key） */
function toTableRow(item: ExecuteLocationItem): ExecuteLocationTableRow {
  const normalized = normalizeExecuteLocationRow(item);
  return {
    ...normalized,
    _clientKey: normalized.id ? `persisted:${normalized.id}` : nextClientKey(),
  };
}

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

const { run: runPage, loading: pageLoading } = useRequest(pageExecuteLocationApi, {
  manual: true,
});
const { run: runCreate, loading: createLoading } = useRequest(createExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('保存成功');
    getList();
  },
});
const { run: runUpdate, loading: updateLoading } = useRequest(updateExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('保存成功');
    getList();
  },
});
const { run: runDelete, loading: deleteLoading } = useRequest(deleteExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

watch(
  [
    pageLoading,
    createLoading,
    updateLoading,
    deleteLoading,
    batchSaving,
    copyLoading,
    pasteLoading,
  ],
  () => {
    loading.value =
      !!pageLoading.value ||
      !!createLoading.value ||
      !!updateLoading.value ||
      !!deleteLoading.value ||
      !!batchSaving.value ||
      !!copyLoading.value ||
      !!pasteLoading.value;
  },
  { immediate: true },
);

/** 当前开立科室已配置的项目 ID，供批量添加去重 */
const boundActivityDefinitionIds = computed(() =>
  list.value.map((row) => String(row.activityDefinitionId ?? '')).filter((id) => id.length > 0),
);

/** 剪贴板是否有可粘贴规则 */
const hasCopiedRules = computed(() => copiedRuleTemplates.value.length > 0);

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: '5%',
    align: 'center',
  },
  {
    prop: 'activityCategoryCode',
    label: '诊疗大类',
    width: '14%',
    align: 'center',
    slot: 'activityCategory',
  },
  {
    prop: 'activityDefinitionId',
    label: '项目名称',
    width: '16%',
    align: 'center',
    slot: 'activityDefinition',
  },
  {
    prop: 'executionOrgId',
    label: '执行科室',
    width: '16%',
    align: 'center',
    slot: 'executionOrg',
  },
  {
    prop: 'timeRange',
    label: '生效时段',
    width: '28%',
    align: 'center',
    slot: 'timeRange',
  },
  {
    label: '操作',
    width: '14%',
    align: 'center',
    slot: 'actions',
  },
];

/** 初始化开立科室树、执行科室下拉与诊疗大类 */
async function initPage() {
  treeLoading.value = true;
  try {
    const initData = await initExecuteLocationApi();
    const orgOptions = (initData.organizationOptions || []) as ExecuteLocationOrgOption[];
    orgTree.value = mapOrgOptionsToTree(orgOptions);
    // 开立树顶层为医院根，执行科室下拉不得出现院名
    const hospitalRootIds = collectHospitalRootIds(orgOptions);
    // 执行科室：后端 init 已仅返回 DEPARTMENT；兼容树形时只取叶子，并再排除医院根
    const execRaw = (initData.executionOrgOptions || []) as ExecuteLocationOrgOption[];
    const hasNested = execRaw.some((n) => (n.children?.length ?? 0) > 0);
    if (hasNested) {
      executionOrgOptions.value = flattenOrgTree(execRaw, hospitalRootIds);
    } else {
      executionOrgOptions.value = execRaw
        .map((item) => ({
          value: String(item.value ?? '').trim(),
          label: String(item.label ?? item.value ?? '').trim(),
        }))
        .filter((item) => item.value.length > 0 && !hospitalRootIds.has(item.value));
    }
    activityCategoryOptions.value = initData.activityCategoryOptions || [];
  } finally {
    treeLoading.value = false;
  }
}

/** 按开立科室 + 诊疗大类拉取可选项目 */
async function loadActivityDefinitionsForRow(row: ExecuteLocationTableRow) {
  if (!currentOrgId.value || !row.activityCategoryCode) {
    clearRowActivityDefinitionOptions(row);
    return;
  }
  const params: PageActivityDefinitionParams = {
    organizationId: currentOrgId.value,
    activityCategoryCode: row.activityCategoryCode,
    pageNo: 1,
    pageSize: 100,
  };
  const resp = await pageActivityDefinitionForExecuteLocationApi(params);
  const options = normalizeActivityDefinitionOptions(resp.content || []);
  const defId = toActivityDefinitionIdString(row.activityDefinitionId);
  // 当前选中项若不在分页结果中，用列表接口返回的名称回显
  if (defId && !options.some((item) => item.value === defId)) {
    options.unshift({
      value: defId,
      label: row.activityDefinitionName?.trim() || defId,
    });
  }
  setRowActivityDefinitionOptions(row, options);
}

function onActivityCategoryChange(row: ExecuteLocationTableRow) {
  row.activityDefinitionId = undefined;
  clearRowActivityDefinitionOptions(row);
}

/** 加载当前开立科室执行规则列表 */
async function getList() {
  const requestSeq = ++listRequestSeq;
  if (!currentOrgId.value) {
    list.value = [];
    total.value = 0;
    return;
  }
  queryParams.organizationId = currentOrgId.value;
  const params: PageExecuteLocationParams = {
    organizationId: queryParams.organizationId,
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  };

  try {
    const resp = await runPage(params);
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = (resp.content || []).map((item) => toTableRow(item));
    total.value = resp.totalCount || 0;
    rowActivityDefinitionOptions.value = {};
    for (const row of list.value) {
      hydrateRowActivityDefinitionOption(row);
    }
  } catch {
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = [];
    total.value = 0;
    ElMessage.error('加载执行规则失败，请稍后重试');
  }
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function isOrgTreeLeafNode(data: OrgTreeNode, treeNode: { isLeaf?: boolean }): boolean {
  if (treeNode.isLeaf === false) {
    return false;
  }
  const children = data.children;
  return !children || children.length === 0;
}

/** 选中左侧开立科室叶子节点 */
function handleOrgNodeClick(data: OrgTreeNode, treeNode: { isLeaf?: boolean }) {
  if (!isOrgTreeLeafNode(data, treeNode)) {
    return;
  }
  currentOrgId.value = data.id;
  currentOrgName.value = data.name;
  queryParams.pageNo = 1;
  getList();
}

/** 校验单行，返回错误文案；通过则返回 undefined */
function getRowValidationError(row: ExecuteLocationTableRow): string | undefined {
  if (!row.activityCategoryCode) {
    return '诊疗大类不能为空';
  }
  if (!String(row.executionOrgId ?? '').trim()) {
    return '执行科室不能为空';
  }
  if (!row.startTime || !row.endTime) {
    return '开始/结束时间不能为空';
  }
  const startSeconds = parseTimeToSeconds(row.startTime);
  const endSeconds = parseTimeToSeconds(row.endTime);
  if (startSeconds === undefined || endSeconds === undefined) {
    return '开始/结束时间格式不正确';
  }
  if (startSeconds > endSeconds) {
    return '开始时间不能大于结束时间';
  }
  return undefined;
}

/** 校验单行并弹错 */
function validateRow(row: ExecuteLocationTableRow): boolean {
  const error = getRowValidationError(row);
  if (error) {
    ElMessage.error(error);
    return false;
  }
  return true;
}

/** 组装单行创建/更新请求体 */
function buildSavePayload(
  row: ExecuteLocationTableRow,
): CreateExecuteLocationParams | UpdateExecuteLocationParams {
  const normalizedId = isPersistedRowId(row.id) ? String(row.id) : undefined;
  return {
    ...(normalizedId ? { id: normalizedId } : {}),
    organizationId: String(row.organizationId ?? ''),
    activityDefinitionId: row.activityDefinitionId,
    activityCategoryCode: String(row.activityCategoryCode ?? ''),
    executionOrgId: String(row.executionOrgId ?? ''),
    executionLocationId: row.executionLocationId,
    startTime: String(row.startTime ?? ''),
    endTime: String(row.endTime ?? ''),
    priority: 1,
    displayOrder: row.displayOrder,
  };
}

function handleAddRow() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }
  list.value.unshift({
    _clientKey: nextClientKey(),
    organizationId: currentOrgId.value,
    activityCategoryCode: undefined,
    activityDefinitionId: undefined,
    executionOrgId: undefined,
    startTime: '00:00:00',
    endTime: '23:59:59',
    priority: 1,
  });
}

async function handleSaveRow(row: ExecuteLocationTableRow) {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择开立科室');
    return;
  }
  row.organizationId = currentOrgId.value;

  if (!validateRow(row)) {
    return;
  }

  const payload = buildSavePayload(row);
  const normalizedId = isPersistedRowId(row.id) ? String(row.id) : undefined;

  await (normalizedId
    ? runUpdate(payload as UpdateExecuteLocationParams)
    : runCreate(payload as CreateExecuteLocationParams));
}

/** 批量保存当前列表全部行：新增走 create/batchCreate，已落库走 update */
async function handleBatchSave() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择开立科室');
    return;
  }
  if (!list.value.length) {
    ElMessage.warning('当前没有可保存的数据');
    return;
  }

  // 先整表校验，失败时指出行号
  for (let index = 0; index < list.value.length; index += 1) {
    const row = list.value[index];
    if (!row) {
      continue;
    }
    row.organizationId = currentOrgId.value;
    const error = getRowValidationError(row);
    if (error) {
      ElMessage.error(`第 ${index + 1} 行：${error}`);
      return;
    }
  }

  try {
    await ElMessageBox.confirm(
      `将保存当前列表共 ${list.value.length} 条规则，确定继续吗？`,
      '批量保存',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  // 拆分：已落库待更新 / 未落库待新建
  const updateRows = list.value.filter((row) => isPersistedRowId(row.id));
  const createRows = list.value.filter((row) => !isPersistedRowId(row.id));

  batchSaving.value = true;
  let succeeded = 0;
  try {
    // 已落库行逐条更新
    for (const row of updateRows) {
      const displayIndex = list.value.indexOf(row) + 1;
      try {
        await updateExecuteLocationApi(buildSavePayload(row) as UpdateExecuteLocationParams);
        succeeded += 1;
      } catch {
        ElMessage.error(`第 ${displayIndex} 行保存失败（已成功 ${succeeded} 条），请修正后重试`);
        await getList();
        return;
      }
    }

    // 新建行按大类+执行科室+时段+地点分组：有项目 ID 走 batchCreate，大类默认行走单条 create
    const groups = new Map<string, ExecuteLocationTableRow[]>();
    for (const row of createRows) {
      const groupKey = [
        row.activityCategoryCode ?? '',
        row.executionOrgId ?? '',
        row.startTime ?? '',
        row.endTime ?? '',
        row.executionLocationId ?? '',
      ].join('|');
      const bucket = groups.get(groupKey) ?? [];
      bucket.push(row);
      groups.set(groupKey, bucket);
    }

    for (const group of groups.values()) {
      const first = group[0];
      if (!first) {
        continue;
      }
      const withDefinition = group.filter((row) => !!row.activityDefinitionId);
      const categoryDefaults = group.filter((row) => !row.activityDefinitionId);

      if (withDefinition.length > 0) {
        try {
          await batchCreateExecuteLocationApi({
            organizationId: currentOrgId.value,
            executionOrgId: String(first.executionOrgId ?? ''),
            activityCategoryCode: String(first.activityCategoryCode ?? ''),
            activityDefinitionIds: withDefinition.map((row) => String(row.activityDefinitionId)),
            executionLocationId: first.executionLocationId,
            startTime: String(first.startTime ?? ''),
            endTime: String(first.endTime ?? ''),
            priority: 1,
            displayOrder: first.displayOrder,
          });
          succeeded += withDefinition.length;
        } catch {
          ElMessage.error(`批量新建失败（已成功 ${succeeded} 条），请修正后重试`);
          await getList();
          return;
        }
      }

      for (const row of categoryDefaults) {
        const displayIndex = list.value.indexOf(row) + 1;
        try {
          await createExecuteLocationApi(buildSavePayload(row) as CreateExecuteLocationParams);
          succeeded += 1;
        } catch {
          ElMessage.error(`第 ${displayIndex} 行保存失败（已成功 ${succeeded} 条），请修正后重试`);
          await getList();
          return;
        }
      }
    }

    ElMessage.success(`已批量保存 ${succeeded} 条`);
    await getList();
  } finally {
    batchSaving.value = false;
  }
}

async function handleDeleteRow(row: ExecuteLocationTableRow, index: number) {
  const persistedId = isPersistedRowId(row.id) ? String(row.id) : undefined;

  if (persistedId) {
    await runDelete(persistedId);
    return;
  }

  list.value.splice(index, 1);
  clearRowActivityDefinitionOptions(row);
}

/** 打开批量添加弹窗 */
function openBatchDialog() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }
  batchDialogVisible.value = true;
}

/** 批量添加确认：写入表格草稿行，由用户核对后批量保存整表 */
function handleBatchConfirm(payload: BatchAddConfirmPayload) {
  const startSeconds = parseTimeToSeconds(payload.startTime);
  const endSeconds = parseTimeToSeconds(payload.endTime);
  if (startSeconds === undefined || endSeconds === undefined) {
    ElMessage.error('生效时段格式不正确');
    return;
  }
  if (startSeconds > endSeconds) {
    ElMessage.error('开始时间不能大于结束时间');
    return;
  }
  if (!String(payload.executionOrgId ?? '').trim()) {
    ElMessage.error('执行科室不能为空');
    return;
  }

  for (const item of payload.items) {
    const option = normalizeActivityDefinitionOptions([item])[0];
    if (!option) {
      continue;
    }
    const row: ExecuteLocationTableRow = {
      _clientKey: nextClientKey(),
      organizationId: currentOrgId.value,
      activityCategoryCode: payload.activityCategoryCode,
      activityDefinitionId: option.value,
      activityDefinitionName: option.label,
      executionOrgId: payload.executionOrgId,
      startTime: payload.startTime,
      endTime: payload.endTime,
      priority: 1,
    };
    list.value.unshift(row);
    setRowActivityDefinitionOptions(row, [option]);
  }
  ElMessage.success(`已添加 ${payload.items.length} 条项目配置，请核对后点击批量保存`);
}

/** 规则去重键：大类+项目+执行科室+地点+时段（粘贴时跳过目标科室已有同配置） */
function buildRuleConfigKey(
  row: Pick<
    ExecuteLocationItem,
    | 'activityCategoryCode'
    | 'activityDefinitionId'
    | 'executionOrgId'
    | 'executionLocationId'
    | 'startTime'
    | 'endTime'
  >,
): string {
  return [
    String(row.activityCategoryCode ?? ''),
    toActivityDefinitionIdString(row.activityDefinitionId),
    String(row.executionOrgId ?? ''),
    String(row.executionLocationId ?? ''),
    String(row.startTime ?? ''),
    String(row.endTime ?? ''),
  ].join('|');
}

/** 将规则行转为可跨科室粘贴的模板 */
function toCopiedTemplates(rows: ExecuteLocationItem[]): CopiedRuleTemplate[] {
  return rows.map((row) => ({
    activityCategoryCode: row.activityCategoryCode,
    activityDefinitionId: toOptionalIdString(row.activityDefinitionId),
    activityDefinitionName: row.activityDefinitionName,
    executionOrgId: toOptionalIdString(row.executionOrgId),
    executionLocationId: toOptionalIdString(row.executionLocationId),
    startTime: row.startTime,
    endTime: row.endTime,
    displayOrder: row.displayOrder,
  }));
}

/**
 * 分页拉取某开立科室的全部已落库规则（复制/粘贴去重用，不限于当前页）。
 */
async function fetchAllExecuteLocations(organizationId: string): Promise<ExecuteLocationItem[]> {
  const all: ExecuteLocationItem[] = [];
  let pageNo = 1;
  let totalCount = Number.POSITIVE_INFINITY;

  // 按页累加，直到收齐 totalCount 或本页为空
  while (all.length < totalCount) {
    const resp = await pageExecuteLocationApi({
      organizationId,
      pageNo,
      pageSize: FETCH_ALL_PAGE_SIZE,
    });
    const content = resp.content || [];
    totalCount = resp.totalCount ?? 0;
    for (const item of content) {
      all.push(normalizeExecuteLocationRow(item));
    }
    if (content.length === 0 || totalCount === 0) {
      break;
    }
    pageNo += 1;
    if (pageNo > 500) {
      break;
    }
  }
  return all;
}

/** 复制当前开立科室全部页规则到剪贴板，供其他科室粘贴 */
async function handleCopyAll() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择开立科室');
    return;
  }

  copyLoading.value = true;
  try {
    const allRows = await fetchAllExecuteLocations(currentOrgId.value);
    if (!allRows.length) {
      ElMessage.warning('当前科室没有可复制的规则');
      return;
    }
    copiedRuleTemplates.value = toCopiedTemplates(allRows);
    copiedFromOrgId.value = currentOrgId.value;
    copiedFromOrgName.value = currentOrgName.value;
    ElMessage.success(
      `已复制全部 ${copiedRuleTemplates.value.length} 条规则（来源：${currentOrgName.value}）`,
    );
  } catch {
    ElMessage.error('复制失败，请稍后重试');
  } finally {
    copyLoading.value = false;
  }
}

/** 将剪贴板规则以新增草稿粘贴到当前科室（跳过与目标全量已有配置重复的行） */
async function handlePaste() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择目标科室');
    return;
  }
  if (!copiedRuleTemplates.value.length) {
    ElMessage.warning('请先在其他科室点击「复制全部配置」');
    return;
  }
  if (copiedFromOrgId.value && copiedFromOrgId.value === currentOrgId.value) {
    ElMessage.warning('不能粘贴到来源科室，请先切换到其他开立科室');
    return;
  }

  const sourceHint = copiedFromOrgName.value ? `（来源：${copiedFromOrgName.value}）` : '';
  try {
    await ElMessageBox.confirm(
      `将把剪贴板中 ${copiedRuleTemplates.value.length} 条规则${sourceHint}粘贴到「${currentOrgName.value}」；与目标科室已有相同配置的将跳过。粘贴后请核对并点击批量保存。确定继续吗？`,
      '粘贴配置',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  pasteLoading.value = true;
  try {
    // 目标科室全量已落库规则 + 当前页未保存草稿，一并参与去重
    const existingPersisted = await fetchAllExecuteLocations(currentOrgId.value);
    const existingKeys = new Set<string>();
    for (const row of existingPersisted) {
      existingKeys.add(buildRuleConfigKey(row));
    }
    for (const row of list.value) {
      existingKeys.add(buildRuleConfigKey(row));
    }

    let addedCount = 0;
    let skippedCount = 0;
    // 逐条按新增草稿写入当前列表；重复配置跳过
    for (const template of copiedRuleTemplates.value) {
      const key = buildRuleConfigKey(template);
      if (existingKeys.has(key)) {
        skippedCount += 1;
        continue;
      }
      const row: ExecuteLocationTableRow = {
        _clientKey: nextClientKey(),
        organizationId: currentOrgId.value,
        activityCategoryCode: template.activityCategoryCode,
        activityDefinitionId: template.activityDefinitionId,
        activityDefinitionName: template.activityDefinitionName,
        executionOrgId: template.executionOrgId,
        executionLocationId: template.executionLocationId,
        startTime: template.startTime || '00:00:00',
        endTime: template.endTime || '23:59:59',
        priority: 1,
        displayOrder: template.displayOrder,
      };
      list.value.unshift(row);
      if (row.activityDefinitionId) {
        hydrateRowActivityDefinitionOption(row);
      }
      existingKeys.add(key);
      addedCount += 1;
    }

    if (!addedCount) {
      ElMessage.warning('粘贴完成：目标科室已存在相同配置，未新增任何行');
      return;
    }
    const skipHint = skippedCount ? `，跳过重复 ${skippedCount} 条` : '';
    ElMessage.success(
      `已粘贴新增 ${addedCount} 条规则${sourceHint}${skipHint}，请核对后点击批量保存（切换分页或科室前须先保存）`,
    );
  } catch {
    ElMessage.error('粘贴失败，请稍后重试');
  } finally {
    pasteLoading.value = false;
  }
}

onMounted(async () => {
  await initPage();
});

onActivated(() => {
  if (currentOrgId.value) {
    getList();
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
        <div class="min-h-0 flex-1 overflow-auto" v-loading="treeLoading">
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

      <div class="execute-location-panel min-w-0 flex-1">
        <div v-if="currentOrgId" class="mb-2 flex flex-wrap items-center gap-2 text-sm">
          <span class="text-[var(--el-text-color-regular)]">
            当前开立科室：
            <span class="font-semibold text-[var(--el-text-color-primary)]">{{
              currentOrgName
            }}</span>
            ；按诊疗大类/项目配置执行科室。可「复制全部配置」（含所有分页）后切换到其他科室粘贴。
          </span>
          <ElTag v-if="hasCopiedRules" type="info">
            已复制 {{ copiedRuleTemplates.length }} 条{{
              copiedFromOrgName ? `（${copiedFromOrgName}）` : ''
            }}
          </ElTag>
        </div>
        <TableLayout
          class="execute-location-table"
          :table-data="list"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :table-columns="tableColumns"
          :show-top-query="false"
          :show-pagination="true"
          row-key="_clientKey"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="handleAddRow" :disabled="!currentOrgId">
              添加新项目
            </ElButton>
            <ElButton @click="openBatchDialog" :disabled="!currentOrgId"> 批量添加项目 </ElButton>
            <ElButton
              type="success"
              :disabled="!currentOrgId || !list.length"
              :loading="batchSaving"
              @click="handleBatchSave"
            >
              批量保存{{ list.length ? `（${list.length}）` : '' }}
            </ElButton>
            <ElButton plain :disabled="!currentOrgId" :loading="copyLoading" @click="handleCopyAll">
              复制全部配置
            </ElButton>
            <ElButton
              plain
              :disabled="!currentOrgId || !hasCopiedRules"
              :loading="pasteLoading"
              @click="handlePaste"
            >
              粘贴
            </ElButton>
          </template>

          <template #activityCategory="{ row }">
            <ElSelect
              v-model="row.activityCategoryCode"
              placeholder="请选择诊疗大类"
              clearable
              class="cell-control"
              @change="() => onActivityCategoryChange(row)"
            >
              <ElOption
                v-for="item in activityCategoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #activityDefinition="{ row }">
            <ElSelect
              v-model="row.activityDefinitionId"
              filterable
              clearable
              placeholder="可选，不选表示大类默认"
              class="cell-control"
              @visible-change="(visible: boolean) => visible && loadActivityDefinitionsForRow(row)"
            >
              <ElOption
                v-for="item in getActivityDefinitionOptionsForRow(row)"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #executionOrg="{ row }">
            <ElSelect
              v-model="row.executionOrgId"
              filterable
              clearable
              placeholder="请选择执行科室"
              class="cell-control"
            >
              <ElOption
                v-for="item in executionOrgOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #timeRange="{ row }">
            <div class="execute-time-row">
              <ElTimePicker
                v-model="row.startTime"
                placeholder="开始"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="execute-time-input"
              />
              <span class="execute-time-sep">~</span>
              <ElTimePicker
                v-model="row.endTime"
                placeholder="结束"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="execute-time-input"
              />
            </div>
          </template>

          <template #actions="{ row, rowIndex }">
            <ElButton link type="primary" @click="handleSaveRow(row)">保存</ElButton>
            <ElButton link type="danger" @click="handleDeleteRow(row, rowIndex)">删除</ElButton>
          </template>
        </TableLayout>
      </div>
    </div>

    <BatchAddDialog
      v-model:visible="batchDialogVisible"
      :organization-id="currentOrgId"
      :activity-category-options="activityCategoryOptions"
      :execution-org-options="executionOrgOptions"
      :bound-activity-definition-ids="boundActivityDefinitionIds"
      @confirm="handleBatchConfirm"
    />
  </Page>
</template>

<style scoped lang="scss">
.execute-location-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.execute-location-table {
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

.execute-time-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding-inline: 8px;
  box-sizing: border-box;
}

.execute-time-input {
  flex: 1;
  min-width: 0;
  max-width: 168px;
}

.execute-time-sep {
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}
</style>
