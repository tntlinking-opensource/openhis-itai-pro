<script lang="ts" setup>
import type { WardBed, WardBedInitResponse } from '#/api/basic/ward-bed';

import { computed, onActivated, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteWardBedApi,
  disableWardBedApi,
  enableWardBedApi,
  getWardBedPageApi,
  getWardListApi,
  wardBedInitApi,
  WARD_BED_FORM_CODE,
  WARD_BED_STATUS_CODE,
} from '#/api/basic/ward-bed';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'WardBedManage',
});

type WardBedRow = WardBed & {
  children?: WardBedRow[];
};

interface WardSection {
  ward: WardBedRow;
  rooms: WardBedRow[];
}

/** 树表行：附带层级文案与所属病区上下文 */
type WardBedTreeRow = WardBedRow & {
  children?: WardBedTreeRow[];
  __section?: WardSection;
  __subCount?: string;
};

const loading = ref(false);
const filterKeyword = ref('');
const initOptions = ref<WardBedInitResponse>();
const organizationOptions = ref<Array<{ label: string; value: string }>>([]);
const selectedOrganizationId = ref<string>('');
const allWards = ref<WardBedRow[]>([]);
const wardSections = ref<WardSection[]>([]);

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<WardBed & { id: string; roomParentId?: string }>>({});
const dialogParentWard = ref<WardBedRow | null>(null);
const dialogRoomList = ref<WardBedRow[]>([]);
const dialogLockFormEnum = ref(false);

const statusLabelMap = computed(() => {
  const map = new Map<string, string>();
  (initOptions.value?.locationStatusOptions || []).forEach((item) => {
    map.set(String(item.value), item.label);
  });
  return map;
});

/** 当前机构下病区（列表接口已按机构过滤，此处仅做空机构兜底） */
const orgWards = computed(() => {
  if (!selectedOrganizationId.value) return [];
  return allWards.value;
});

/** 按关键词过滤病区/病房/床位 */
const filteredSections = computed(() => {
  const keyword = filterKeyword.value.trim().toLowerCase();
  if (!keyword) return wardSections.value;

  return wardSections.value
    .map((section) => {
      const wardName = String(section.ward.name || '').toLowerCase();
      const wardHit = wardName.includes(keyword);

      const rooms = section.rooms
        .map((room) => {
          const roomName = String(room.name || '').toLowerCase();
          const roomHit = roomName.includes(keyword);
          const beds = ((room.children as WardBedRow[] | undefined) || []).filter((bed) =>
            String(bed.name || '')
              .toLowerCase()
              .includes(keyword),
          );

          if (wardHit || roomHit) return room;
          if (beds.length > 0) return { ...room, children: beds };
          return null;
        })
        .filter((room): room is WardBedRow => room !== null);

      if (wardHit) return section;
      if (rooms.length > 0) return { ...section, rooms };
      return null;
    })
    .filter((section): section is WardSection => section !== null);
});

/** 树表数据：病区 → 病房 → 床位，默认全展开 */
const treeTableData = computed(() => {
  return filteredSections.value.map((section) => {
    const bedCount = section.rooms.reduce((sum, room) => sum + getRoomBedCount(room), 0);
    const wardNode: WardBedTreeRow = {
      ...section.ward,
      __section: section,
      __subCount: `${section.rooms.length} 间病房 · ${bedCount} 张床`,
      children: section.rooms.map((room) => ({
        ...room,
        __section: section,
        __subCount: `${getRoomBedCount(room)} 张床`,
        children: ((room.children as WardBedRow[] | undefined) || []).map((bed) => ({
          ...bed,
          __section: section,
          __subCount: '—',
        })),
      })),
    };
    return wardNode;
  });
});

/** 统计病房下床位数量（由下级床位节点汇总，非手工填报） */
function getRoomBedCount(room: WardBedRow) {
  const children = (room.children as WardBedRow[] | undefined) || [];
  return children.length;
}

/** 层级类型文案 */
function getLevelLabel(formEnum?: string) {
  if (formEnum === WARD_BED_FORM_CODE.WARD) return '病区';
  if (formEnum === WARD_BED_FORM_CODE.HOUSE) return '病房';
  if (formEnum === WARD_BED_FORM_CODE.BED) return '床位';
  return '—';
}

/** 状态标签类型 */
function getStatusTagType(statusEnum?: string) {
  return statusEnum === WARD_BED_STATUS_CODE.ACTIVE ? 'success' : 'info';
}

/** 加载机构下拉等初始化数据 */
async function loadInitOptions() {
  const response = await wardBedInitApi();
  initOptions.value = response;
  organizationOptions.value = (response?.organizationOptions || []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  if (!selectedOrganizationId.value && organizationOptions.value.length) {
    selectedOrganizationId.value = organizationOptions.value[0]?.value || '';
  }
}

/** 将接口行归一化：雪花 ID 统一 string，避免比较/树表 row-key 异常 */
function normalizeWardBedRow(item: WardBed): WardBedRow {
  const id = preserveApiIdForRequest(item.id);
  const organizationId = preserveApiIdForRequest(item.organizationId);
  const parentId = preserveApiIdForRequest(item.parentId);
  const children = unwrapApiList<WardBed>(item.children).map((child) => normalizeWardBedRow(child));
  return {
    ...item,
    id,
    organizationId,
    parentId,
    children: children.length > 0 ? children : undefined,
  };
}

/** 按当前选中机构拉取病区列表 */
async function loadWards() {
  const orgId = selectedOrganizationId.value;
  if (!orgId) {
    allWards.value = [];
    return;
  }
  const response = await getWardListApi({ id: orgId });
  allWards.value = unwrapApiList<WardBed>(response).map((item) => normalizeWardBedRow(item));
}

/** 并行加载当前机构下每个病区的病房与床位 */
async function loadWardSections() {
  const wards = orgWards.value.filter((ward) => ward.id);
  if (!wards.length) {
    wardSections.value = [];
    return;
  }
  wardSections.value = await Promise.all(
    wards.map(async (ward) => {
      const wardId = String(ward.id);
      const rooms = unwrapApiList<WardBed>(await getWardBedPageApi({ id: wardId })).map((item) =>
        normalizeWardBedRow(item),
      );
      return { ward, rooms };
    }),
  );
}

/** 刷新整页数据 */
async function refreshAll() {
  loading.value = true;
  try {
    await loadInitOptions();
    await loadWards();
    await loadWardSections();
  } catch (error) {
    console.error('加载病区病房病床失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

/** 打开新增/编辑弹窗 */
function openDialog(options: {
  formData: Partial<WardBed & { id: string; roomParentId?: string }>;
  lockFormEnum?: boolean;
  parentWard?: WardBedRow | null;
  roomList?: WardBedRow[];
}) {
  dialogFormData.value = options.formData;
  dialogLockFormEnum.value = !!options.lockFormEnum;
  dialogParentWard.value = options.parentWard ?? null;
  dialogRoomList.value = options.roomList ?? [];
  dialogVisible.value = true;
}

/** 新增病区 */
function handleAddWard() {
  if (!selectedOrganizationId.value) {
    ElMessage.warning('请先选择机构');
    return;
  }
  openDialog({
    lockFormEnum: true,
    formData: {
      formEnum: WARD_BED_FORM_CODE.WARD,
      organizationId: selectedOrganizationId.value,
    },
  });
}

/** 从病区或当前选中机构解析创建用的 organizationId（雪花 ID 须保持 string） */
function resolveOrganizationIdForWard(ward: WardBedRow): string | undefined {
  const fromWard = ward.organizationId;
  if (fromWard !== null && fromWard !== undefined && String(fromWard).trim() !== '') {
    return String(fromWard);
  }
  const fromSelected = selectedOrganizationId.value?.trim();
  return fromSelected || undefined;
}

/** 在指定病区下新增病房 */
function handleAddRoom(section: WardSection) {
  openDialog({
    lockFormEnum: true,
    parentWard: section.ward,
    formData: {
      formEnum: WARD_BED_FORM_CODE.HOUSE,
      parentId: section.ward.id,
      organizationId: resolveOrganizationIdForWard(section.ward),
    },
  });
}

/** 在指定病房下新增床位 */
function handleAddBed(section: WardSection, room: WardBedRow) {
  openDialog({
    lockFormEnum: true,
    parentWard: section.ward,
    roomList: section.rooms,
    formData: {
      formEnum: WARD_BED_FORM_CODE.BED,
      parentId: section.ward.id,
      roomParentId: room.id,
      organizationId: resolveOrganizationIdForWard(section.ward),
    },
  });
}

/** 编辑病区/病房/床位 */
function handleEdit(row: WardBedTreeRow) {
  const section = row.__section;
  const formEnum = row.formEnum;
  if (formEnum === WARD_BED_FORM_CODE.BED) {
    openDialog({
      lockFormEnum: true,
      parentWard: section?.ward ?? null,
      roomList: section?.rooms ?? [],
      formData: {
        id: row.id,
        formEnum,
        roomParentId: row.parentId ? String(row.parentId) : undefined,
      },
    });
    return;
  }
  if (formEnum === WARD_BED_FORM_CODE.HOUSE) {
    openDialog({
      lockFormEnum: true,
      parentWard: section?.ward ?? null,
      formData: { id: row.id, formEnum },
    });
    return;
  }
  openDialog({
    lockFormEnum: true,
    formData: { id: row.id, formEnum: WARD_BED_FORM_CODE.WARD },
  });
}

/** 启用或停用位置 */
async function toggleStatus(row: WardBedRow) {
  if (!row.id) return;
  const enabled = row.statusEnum !== WARD_BED_STATUS_CODE.ACTIVE;
  const actionText = enabled ? '启用' : '停用';
  try {
    await ElMessageBox.confirm(`确定要${actionText}「${row.name || ''}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    if (enabled) await enableWardBedApi(String(row.id));
    else await disableWardBedApi(String(row.id));
    ElMessage.success(`${actionText}成功`);
    await refreshAll();
  } catch (error: unknown) {
    if (error === 'cancel') return;
    const message = error instanceof Error ? error.message : `${actionText}失败，请稍后重试`;
    ElMessage.error(message);
  }
}

/** 删除位置 */
async function handleDelete(row: WardBedRow) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确定要删除「${row.name || ''}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteWardBedApi(String(row.id));
    ElMessage.success('删除成功');
    await refreshAll();
  } catch (error: unknown) {
    if (error === 'cancel') return;
    const message = error instanceof Error ? error.message : '删除失败，请稍后重试';
    ElMessage.error(message);
  }
}

/** 床位状态展示文案 */
function getStatusLabel(statusEnum?: string) {
  if (!statusEnum) return '未知';
  return statusLabelMap.value.get(statusEnum) || statusEnum;
}

/** 弹窗保存成功后刷新 */
async function handleDialogSuccess() {
  await refreshAll();
}

watch(selectedOrganizationId, async (organizationId) => {
  if (!organizationId || loading.value) return;
  loading.value = true;
  try {
    await loadWards();
    await loadWardSections();
  } catch (error) {
    console.error('切换机构后加载病区失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
  } finally {
    loading.value = false;
  }
});

onMounted(() => {
  refreshAll();
});

onActivated(async () => {
  const needRefresh = sessionStorage.getItem('wardBedRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('wardBedRefresh');
    await refreshAll();
  }
});
</script>

<template>
  <Page auto-content-height>
    <div v-loading="loading" class="wardbed-list">
      <div class="wardbed-list__toolbar">
        <span class="wardbed-list__label">机构</span>
        <el-select
          v-model="selectedOrganizationId"
          class="wardbed-list__org"
          filterable
          placeholder="请选择机构"
        >
          <el-option
            v-for="item in organizationOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-model="filterKeyword"
          class="wardbed-list__search"
          clearable
          placeholder="搜索病区 / 病房 / 床位"
          :prefix-icon="Search"
        />
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="handleAddWard">新增病区</el-button>
      </div>

      <el-table
        class="wardbed-list__table"
        :data="treeTableData"
        row-key="id"
        border
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
        empty-text="暂无数据"
      >
        <el-table-column label="名称" min-width="220" prop="name" show-overflow-tooltip />
        <el-table-column label="类型" width="88" align="center">
          <template #default="{ row }">
            {{ getLevelLabel(row.formEnum) }}
          </template>
        </el-table-column>
        <el-table-column label="下级数量" width="160" align="center">
          <template #default="{ row }">
            {{ row.__subCount || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusTagType(row.statusEnum)" effect="plain">
              {{ getStatusLabel(row.statusEnum) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.formEnum === WARD_BED_FORM_CODE.WARD">
              <el-button type="primary" link @click="handleAddRoom(row.__section!)">
                新增病房
              </el-button>
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.statusEnum !== WARD_BED_STATUS_CODE.ACTIVE"
                type="success"
                link
                @click="toggleStatus(row)"
              >
                启用
              </el-button>
              <el-button v-else type="warning" link @click="toggleStatus(row)">停用</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="row.formEnum === WARD_BED_FORM_CODE.HOUSE">
              <el-button type="primary" link @click="handleAddBed(row.__section!, row)">
                新增床位
              </el-button>
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.statusEnum !== WARD_BED_STATUS_CODE.ACTIVE"
                type="success"
                link
                @click="toggleStatus(row)"
              >
                启用
              </el-button>
              <el-button v-else type="warning" link @click="toggleStatus(row)">停用</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.statusEnum !== WARD_BED_STATUS_CODE.ACTIVE"
                type="success"
                link
                @click="toggleStatus(row)"
              >
                启用
              </el-button>
              <el-button v-else type="warning" link @click="toggleStatus(row)">停用</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <div class="wardbed-list__empty">
            <span>{{
              filterKeyword
                ? '没有匹配的数据'
                : selectedOrganizationId
                  ? '该机构下暂无病区，请点击「新增病区」'
                  : '请先选择机构'
            }}</span>
            <el-button
              v-if="selectedOrganizationId && !filterKeyword"
              type="primary"
              link
              :icon="Plus"
              @click="handleAddWard"
            >
              新增病区
            </el-button>
          </div>
        </template>
      </el-table>
    </div>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :parent-ward="dialogParentWard"
      :ward-list="orgWards"
      :room-list="dialogRoomList"
      :lock-form-enum="dialogLockFormEnum"
      @success="handleDialogSuccess"
    />
  </Page>
</template>

<style scoped>
.wardbed-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.wardbed-list__toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.wardbed-list__label {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.wardbed-list__org {
  width: 200px;
}

.wardbed-list__search {
  width: 260px;
}

.wardbed-list__table {
  flex: 1;
  min-height: 0;
}

.wardbed-list__empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 24px 0;
  color: var(--el-text-color-secondary);
}
</style>
