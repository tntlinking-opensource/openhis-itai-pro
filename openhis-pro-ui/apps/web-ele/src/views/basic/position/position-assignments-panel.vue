<script lang="ts" setup>
import type { Department } from '#/api/core/system/department.types';

import {
  PRACTITIONER_BUSINESS_ROLE_OPTIONS,
  activatePractitionerRoleApi,
  assignPractitionerRoleApi,
  deactivatePractitionerRoleApi,
  editPractitionerRoleApi,
  getLocationOptionByIdApi,
  listLocationOptionsByOrganizationApi,
  listPractitionerAssignmentsApi,
  retirePractitionerRoleApi,
} from '#/api/basic/position';

import type {
  AssignPractitionerRoleItemPayload,
  EditPractitionerRoleItemPayload,
  LocationOptionDto,
  PractitionerAssignmentRow,
} from '#/api/basic/position';

import { computed, onMounted, ref, watch } from 'vue';

import { getDepartmentTreeApi } from '#/api/core/system/department';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

const props = defineProps<{
  practitionerId?: string;
  /** 当前选中用户展示：用户名（姓名） */
  userSummary?: string;
}>();

const rows = ref<PractitionerAssignmentRow[]>([]);
const loading = ref(false);
const orgOptions = ref<{ label: string; value: string }[]>([]);
const locationOptionsForAdd = ref<{ label: string; value: string }[]>([]);
const locationOptionsForEdit = ref<{ label: string; value: string }[]>([]);

const showAddForm = ref(false);
const addForm = ref({
  roleCode: 1,
  orgId: '' as string,
  locationId: '' as string,
  name: '',
});

const editingId = ref<string | null>(null);

interface AssignmentEditDraft {
  assignmentId?: string;
  roleCode?: number;
  orgId?: string;
  locationId?: string;
  name?: string;
  specialtyCode?: string;
  typeCode?: number | null;
}

const editForm = ref<AssignmentEditDraft>({});

function flattenDepartmentTree(
  nodes: Department[],
  prefix = '',
): { label: string; value: string }[] {
  const out: { label: string; value: string }[] = [];
  for (const n of nodes || []) {
    const title = String(n.name ?? n.displayName ?? '');
    if (!title) {
      continue;
    }
    const label = prefix ? `${prefix} / ${title}` : title;
    out.push({ label, value: String(n.id) });
    if (n.children?.length) {
      out.push(...flattenDepartmentTree(n.children, label));
    }
  }
  return out;
}

async function loadOrgOptions() {
  try {
    const raw = await getDepartmentTreeApi();
    const tree = Array.isArray(raw) ? raw : ((raw as { data?: Department[] }).data ?? []);
    orgOptions.value = flattenDepartmentTree(tree);
  } catch {
    orgOptions.value = [];
  }
}

async function loadAssignments() {
  if (!props.practitionerId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listPractitionerAssignmentsApi(props.practitionerId);
    const anyRes = res as PractitionerAssignmentRow[] | { data?: PractitionerAssignmentRow[] };
    const list = Array.isArray(anyRes) ? anyRes : anyRes.data;
    rows.value = Array.isArray(list) ? list : [];
  } catch {
    ElMessage.error('加载岗位列表失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function resetAddForm() {
  addForm.value = {
    roleCode: 1,
    orgId: '',
    locationId: '',
    name: '',
  };
  locationOptionsForAdd.value = [];
  showAddForm.value = false;
}

function toNullableOrgId(v: string): number | null | undefined {
  if (v === '' || v === undefined) {
    return undefined;
  }
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

function toNullableLocationId(v: string | number | undefined): number | null | undefined {
  if (v === undefined || v === null || v === '') {
    return undefined;
  }
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : undefined;
}

async function fetchLocationOptionsForOrg(
  orgId: string,
): Promise<{ label: string; value: string }[]> {
  if (!orgId) {
    return [];
  }
  const res = await listLocationOptionsByOrganizationApi(orgId);
  const raw = res as
    | { data?: { label: string; value: string }[] }
    | { label: string; value: string }[];
  const list = Array.isArray(raw) ? raw : raw.data;
  if (!Array.isArray(list)) {
    return [];
  }
  return list.map((o) => ({ label: o.label, value: String(o.value) }));
}

async function loadLocationOptionsForAdd() {
  locationOptionsForAdd.value = await fetchLocationOptionsForOrg(addForm.value.orgId);
}

function unwrapResultData<T>(res: unknown): T | undefined {
  if (res == null) {
    return undefined;
  }
  if (typeof res === 'object' && res !== null && 'data' in res) {
    return (res as { data?: T }).data as T | undefined;
  }
  return res as T;
}

async function loadLocationOptionsForEdit() {
  const orgId = editForm.value.orgId ?? '';
  let opts = await fetchLocationOptionsForOrg(orgId);
  const curId = editForm.value.locationId;
  if (curId && !opts.some((o) => o.value === String(curId))) {
    const raw = await getLocationOptionByIdApi(curId);
    const extra = unwrapResultData<LocationOptionDto | null | undefined>(raw);
    if (extra?.value != null && extra.label) {
      opts = [{ label: extra.label, value: String(extra.value) }, ...opts];
    }
  }
  locationOptionsForEdit.value = opts;
}

async function onAddOrgChange() {
  addForm.value.locationId = '';
  await loadLocationOptionsForAdd();
}

async function onEditOrgChange() {
  editForm.value.locationId = '';
  await loadLocationOptionsForEdit();
}

async function submitAdd() {
  const pid = props.practitionerId;
  if (!pid) {
    return;
  }
  const orgId = toNullableOrgId(addForm.value.orgId);
  const locId = toNullableLocationId(addForm.value.locationId);
  if (orgId === undefined && (locId === undefined || locId === null)) {
    ElMessage.warning('请至少选择科室或位置');
    return;
  }
  const payload: AssignPractitionerRoleItemPayload = {
    practitionerId: pid,
    roleCode: addForm.value.roleCode,
    name: addForm.value.name?.trim() || undefined,
    orgId: orgId ?? null,
    locationId: locId ?? null,
  };
  try {
    await assignPractitionerRoleApi(payload);
    ElMessage.success('岗位已保存');
    resetAddForm();
    await loadAssignments();
  } catch {
    /* 全局拦截已提示 */
  }
}

async function startEdit(row: PractitionerAssignmentRow) {
  const id = row.assignmentId;
  if (!id) {
    return;
  }
  editingId.value = id;
  editForm.value = {
    assignmentId: id,
    roleCode: row.roleCode,
    orgId:
      row.orgId !== undefined && row.orgId !== null && String(row.orgId) !== ''
        ? String(row.orgId)
        : '',
    locationId:
      row.locationId !== undefined && row.locationId !== null && String(row.locationId) !== ''
        ? String(row.locationId)
        : '',
    name: row.name ?? '',
    specialtyCode: row.specialtyCode,
    typeCode: row.typeCode ?? undefined,
  };
  await loadLocationOptionsForEdit();
}

function cancelEdit() {
  editingId.value = null;
  editForm.value = {};
}

async function saveEdit() {
  const id = editingId.value;
  if (!id) {
    return;
  }
  const orgId = toNullableOrgId(String(editForm.value.orgId ?? ''));
  const locId = toNullableLocationId(editForm.value.locationId);
  if (orgId === undefined && (locId === undefined || locId === null)) {
    ElMessage.warning('科室与位置不可同时为空');
    return;
  }
  const payload: EditPractitionerRoleItemPayload = {
    assignmentId: Number(id),
    name: editForm.value.name?.trim() || undefined,
    orgId: orgId ?? null,
    locationId: locId ?? null,
    specialtyCode: editForm.value.specialtyCode?.trim() || undefined,
    typeCode:
      editForm.value.typeCode === undefined || editForm.value.typeCode === null
        ? undefined
        : Number(editForm.value.typeCode),
  };
  try {
    await editPractitionerRoleApi(payload);
    ElMessage.success('已保存');
    cancelEdit();
    await loadAssignments();
  } catch {
    /* 全局拦截 */
  }
}

async function handleRetire(row: PractitionerAssignmentRow) {
  const id = row.assignmentId;
  if (!id) {
    return;
  }
  try {
    await ElMessageBox.confirm('确定废弃该岗位分配吗？废弃后不可恢复为同一条记录。', '确认', {
      type: 'warning',
    });
    await retirePractitionerRoleApi(Number(id), '工作台废弃');
    ElMessage.success('已废弃');
    await loadAssignments();
  } catch (e: unknown) {
    if (e !== 'cancel') {
      /* error toast */
    }
  }
}

async function handleToggleActive(row: PractitionerAssignmentRow) {
  const id = row.assignmentId;
  if (!id) {
    return;
  }
  try {
    if (row.activeFlag) {
      await deactivatePractitionerRoleApi(Number(id));
      ElMessage.success('已停用');
    } else {
      await activatePractitionerRoleApi(Number(id));
      ElMessage.success('已启用');
    }
    await loadAssignments();
  } catch {
    /* 全局拦截 */
  }
}

const hasPractitioner = computed(() => !!props.practitionerId);

watch(
  () => props.practitionerId,
  () => {
    cancelEdit();
    resetAddForm();
    loadAssignments();
  },
);

watch(showAddForm, async (open) => {
  if (open && addForm.value.orgId) {
    await loadLocationOptionsForAdd();
  }
});

onMounted(async () => {
  await loadOrgOptions();
  await loadAssignments();
});
</script>

<template>
  <div class="position-assignments-panel flex h-full min-h-0 flex-col">
    <div v-if="userSummary" class="text-muted-foreground border-border mb-2 border-b pb-2 text-sm">
      当前用户：{{ userSummary }}
    </div>

    <ElAlert
      v-if="!hasPractitioner"
      show-icon
      title="未绑定医务人员"
      type="warning"
      :closable="false"
    >
      请先在「医务人员」等环节为该用户建立主档后，再分配岗位（adm_pr_assign）。
    </ElAlert>

    <template v-else>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <ElButton type="primary" @click="showAddForm = !showAddForm">
          {{ showAddForm ? '收起新增' : '新增岗位' }}
        </ElButton>
        <ElButton :loading="loading" @click="loadAssignments">刷新</ElButton>
      </div>

      <ElCard v-if="showAddForm" class="mb-4" shadow="never">
        <ElForm :inline="true" label-width="72px" @submit.prevent="submitAdd">
          <ElFormItem label="业务角色" required>
            <ElSelect v-model="addForm.roleCode" placeholder="角色" style="width: 120px">
              <ElOption
                v-for="opt in PRACTITIONER_BUSINESS_ROLE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="科室">
            <ElSelect
              v-model="addForm.orgId"
              clearable
              filterable
              placeholder="机构/科室"
              style="width: 240px"
              @change="onAddOrgChange"
            >
              <ElOption v-for="o in orgOptions" :key="o.value" :label="o.label" :value="o.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="位置">
            <ElSelect
              v-model="addForm.locationId"
              clearable
              filterable
              :disabled="!addForm.orgId"
              placeholder="先选择科室"
              style="width: 280px"
            >
              <ElOption
                v-for="o in locationOptionsForAdd"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="岗位名称">
            <ElInput v-model="addForm.name" clearable placeholder="可选" style="width: 160px" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="submitAdd">保存</ElButton>
            <ElButton @click="resetAddForm">取消</ElButton>
          </ElFormItem>
        </ElForm>
      </ElCard>

      <div class="min-h-0 flex-1 overflow-auto">
        <ElTable
          v-loading="loading"
          :data="rows"
          border
          row-key="assignmentId"
          stripe
          style="width: 100%"
        >
          <ElTableColumn label="业务角色" min-width="100">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElSelect v-model="editForm.roleCode" disabled style="width: 110px">
                  <ElOption
                    v-for="opt in PRACTITIONER_BUSINESS_ROLE_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
              </template>
              <span v-else>{{ row.roleName ?? row.roleCode }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="科室" min-width="180">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElSelect
                  v-model="editForm.orgId"
                  clearable
                  filterable
                  placeholder="科室"
                  style="width: 220px"
                  @change="onEditOrgChange"
                >
                  <ElOption
                    v-for="o in orgOptions"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </ElSelect>
              </template>
              <span v-else>{{ row.orgName ?? '—' }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="位置" min-width="120">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElSelect
                  v-model="editForm.locationId"
                  clearable
                  filterable
                  :disabled="!editForm.orgId"
                  placeholder="先选择科室"
                  style="width: 280px"
                >
                  <ElOption
                    v-for="o in locationOptionsForEdit"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </ElSelect>
              </template>
              <span v-else>
                {{ row.locationName ?? row.locationId ?? '—' }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="岗位名称" min-width="120">
            <template #default="{ row }">
              <ElInput v-if="editingId === row.assignmentId" v-model="editForm.name" />
              <span v-else>{{ row.name ?? '—' }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" label="状态" width="88">
            <template #default="{ row }">
              <ElTag :type="row.activeFlag ? 'success' : 'info'" size="small">
                {{ row.activeFlag ? '启用' : '停用' }}
              </ElTag>
            </template>
          </ElTableColumn>

          <ElTableColumn fixed="right" label="操作" width="220">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElButton link type="primary" @click="saveEdit">保存</ElButton>
                <ElButton link @click="cancelEdit">取消</ElButton>
              </template>
              <template v-else>
                <ElButton link type="primary" @click="startEdit(row)">编辑</ElButton>
                <ElButton link type="warning" @click="handleToggleActive(row)">
                  {{ row.activeFlag ? '停用' : '启用' }}
                </ElButton>
                <ElButton link type="danger" @click="handleRetire(row)">废弃</ElButton>
              </template>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </template>
  </div>
</template>
