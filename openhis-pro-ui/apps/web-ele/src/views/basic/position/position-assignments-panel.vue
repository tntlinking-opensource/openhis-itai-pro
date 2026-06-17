<script lang="ts" setup>
import type { Warehouse } from '#/api/basic/warehouse';
import type { Department } from '#/api/core/system/department.types';

import {
  activatePractitionerRoleApi,
  assignPractitionerRoleApi,
  deactivatePractitionerRoleApi,
  editPractitionerRoleApi,
  getLocationOptionByIdApi,
  legacyRoleCodeToLabel,
  listLocationOptionsByOrganizationApi,
  listPractitionerAssignmentsApi,
  loadBusinessBuiltInRoleOptions,
  PLATFORM_ROLE_CODE,
  retirePractitionerRoleApi,
  type RoleOptionDto,
} from '#/api/basic/position';

import type {
  AssignPractitionerRoleItemPayload,
  EditPractitionerRoleItemPayload,
  LocationOptionDto,
  PractitionerAssignmentRow,
} from '#/api/basic/position';

import { computed, onMounted, ref, watch } from 'vue';

import {
  getWarehouseListApi,
  isPharmacyFormEnum,
  isWarehouseFormEnum,
  WAREHOUSE_STATUS_ENUM,
} from '#/api/basic/warehouse';
import { getDepartmentTreeApi } from '#/api/core/system/department';
import { getRegistrationTypeListApi } from '#/api/core/system/registration';
import SectionTitle from '#/components/layouts/section-title.vue';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import {
  ElAlert,
  ElButton,
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

defineOptions({
  name: 'PositionAssignmentsPanel',
});

const props = defineProps<{
  practitionerId?: string;
  /** 左侧选中用户的登录名，用于区分平台 admin 等场景 */
  userName?: string;
  /** 左侧「系统角色」展示串，与下方业务岗位列表无自动同步关系 */
  roleNames?: string;
}>();

const rows = ref<PractitionerAssignmentRow[]>([]);
const loading = ref(false);
const orgOptions = ref<{ label: string; value: string }[]>([]);
const registrationTypeOptions = ref<{ label: string; value: string }[]>([]);
const locationOptionsForAdd = ref<{ label: string; value: string }[]>([]);
const locationOptionsForEdit = ref<{ label: string; value: string }[]>([]);
const inventoryLocationOptions = ref<{ label: string; value: string }[]>([]);
const businessRoleOptions = ref<RoleOptionDto[]>([]);

const showAddForm = ref(false);
const addForm = ref({
  roleId: '' as string,
  orgId: '' as string,
  locationId: '' as string,
  locationIds: [] as string[],
  name: '',
  serviceIds: [] as string[],
});

const editingId = ref<string | null>(null);

interface AssignmentEditDraft {
  assignmentId?: string;
  roleId?: string;
  orgId?: string;
  locationId?: string;
  locationIds?: string[];
  name?: string;
  specialtyCode?: string;
  typeCode?: number | null;
  serviceIds?: string[];
}

/** 按 roleId 查找内置角色选项 */
function findRoleOption(roleId?: string): RoleOptionDto | undefined {
  if (!roleId) {
    return undefined;
  }
  return businessRoleOptions.value.find((item) => String(item.id) === String(roleId));
}

/** 医师内置角色才维护号源类型 */
function isDoctorAssignment(roleId?: string, roleCode?: null | number): boolean {
  const option = findRoleOption(roleId);
  if (option?.code === PLATFORM_ROLE_CODE.DOCTOR) {
    return true;
  }
  return roleCode === 1;
}

/** 库管角色：位置字段改为管理药库 */
function isWarehouseRole(roleId?: string, roleCode?: null | number): boolean {
  const option = findRoleOption(roleId);
  if (option?.assignmentScopes?.includes('WAREHOUSE')) {
    return true;
  }
  return roleCode === 4;
}

/** 药师角色：位置字段改为管理药房 */
function isPharmacyRole(roleId?: string, roleCode?: null | number): boolean {
  const option = findRoleOption(roleId);
  if (option?.assignmentScopes?.includes('PHARMACY')) {
    return true;
  }
  return roleCode === 3;
}

/** 库管/药师：位置字段改为库房/药房下拉，仍传 locationId */
function usesInventoryLocation(roleId?: string, roleCode?: null | number): boolean {
  return isWarehouseRole(roleId, roleCode) || isPharmacyRole(roleId, roleCode);
}

/** 普通岗位：科室 + 物理位置 */
function usesOrgLocation(roleId?: string, roleCode?: null | number): boolean {
  return !usesInventoryLocation(roleId, roleCode);
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

async function loadRegistrationTypeOptions() {
  try {
    const list = await getRegistrationTypeListApi();
    registrationTypeOptions.value = (list ?? []).map((item) => ({
      label: item.name,
      value: String(item.id),
    }));
  } catch {
    registrationTypeOptions.value = [];
  }
}

/** 加载库房/药房合并下拉选项 */
async function loadInventoryLocationOptions() {
  try {
    const list = await getWarehouseListApi();
    const rows = Array.isArray(list) ? list : [];
    inventoryLocationOptions.value = rows
      .filter(
        (item: Warehouse) =>
          (isWarehouseFormEnum(item.formEnum) || isPharmacyFormEnum(item.formEnum)) &&
          item.statusEnum === WAREHOUSE_STATUS_ENUM.ACTIVE,
      )
      .map((item) => ({ label: item.name, value: String(item.id) }));
  } catch {
    inventoryLocationOptions.value = [];
  }
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
  const pid = preserveApiIdForRequest(props.practitionerId);
  if (!pid) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listPractitionerAssignmentsApi(pid);
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
  const defaultRoleId = businessRoleOptions.value[0]?.id ?? '';
  addForm.value = {
    roleId: defaultRoleId,
    orgId: '',
    locationId: '',
    locationIds: [],
    name: '',
    serviceIds: [],
  };
  locationOptionsForAdd.value = [];
  showAddForm.value = false;
}

function toNullableOrgId(v: string): string | null | undefined {
  const id = preserveApiIdForRequest(v);
  return id ?? undefined;
}

function toNullableLocationId(v: string | undefined): string | null | undefined {
  const id = preserveApiIdForRequest(v);
  return id ?? undefined;
}

/** 将多选 locationId 列表规范为 string[] */
function normalizeLocationIdList(ids: unknown): string[] {
  if (!Array.isArray(ids)) {
    return [];
  }
  return ids.map((id) => preserveApiIdForRequest(id)).filter((id): id is string => !!id);
}

/** 汇总同业务角色下已分配的库房/药房 locationId（编辑回显） */
function collectInventoryLocationIdsForRole(roleCode?: null | number): string[] {
  if (roleCode === undefined || roleCode === null) {
    return [];
  }
  return rows.value
    .filter((row) => row.roleCode === roleCode && row.activeFlag !== false)
    .map((row) =>
      row.locationId != null && String(row.locationId) !== '' ? String(row.locationId) : '',
    )
    .filter((id) => !!id);
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

/** 切换业务角色：清空 location，库房/药房与普通位置互斥 */
function onAddRoleChange() {
  addForm.value.locationId = '';
  addForm.value.locationIds = [];
  addForm.value.orgId = '';
  locationOptionsForAdd.value = [];
}

async function onEditOrgChange() {
  editForm.value.locationId = '';
  await loadLocationOptionsForEdit();
}

async function submitAdd() {
  const pid = preserveApiIdForRequest(props.practitionerId);
  if (!pid) {
    return;
  }
  const roleId = addForm.value.roleId;
  const orgId = toNullableOrgId(addForm.value.orgId);
  const locId = toNullableLocationId(addForm.value.locationId);
  const inventoryLocIds = normalizeLocationIdList(addForm.value.locationIds);

  if (usesInventoryLocation(roleId)) {
    if (inventoryLocIds.length === 0) {
      ElMessage.warning('请选择库房/药房');
      return;
    }
  } else if (orgId === undefined && (locId === undefined || locId === null)) {
    ElMessage.warning('请至少选择科室或位置');
    return;
  }

  const roleIdStr = preserveApiIdForRequest(roleId) ?? undefined;
  const name = addForm.value.name?.trim() || undefined;

  try {
    if (usesInventoryLocation(roleId)) {
      const items: AssignPractitionerRoleItemPayload[] = inventoryLocIds.map((locationId) => ({
        practitionerId: pid,
        roleId: roleIdStr,
        name,
        orgId: null,
        locationId,
      }));
      await assignPractitionerRoleApi(items);
    } else {
      const payload: AssignPractitionerRoleItemPayload = {
        practitionerId: pid,
        roleId: roleIdStr,
        name,
        orgId: orgId ?? null,
        locationId: locId ?? null,
      };
      if (isDoctorAssignment(addForm.value.roleId)) {
        payload.serviceIds = [...addForm.value.serviceIds];
      }
      await assignPractitionerRoleApi(payload);
    }
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
  const roleIdFromRow =
    row.roleId != null && String(row.roleId) !== '' ? String(row.roleId) : undefined;
  const inventory = usesInventoryLocation(roleIdFromRow, row.roleCode);
  editForm.value = {
    assignmentId: id,
    roleId: roleIdFromRow,
    orgId:
      row.orgId !== undefined && row.orgId !== null && String(row.orgId) !== ''
        ? String(row.orgId)
        : '',
    locationId: inventory
      ? ''
      : row.locationId !== undefined && row.locationId !== null && String(row.locationId) !== ''
        ? String(row.locationId)
        : '',
    locationIds: inventory ? collectInventoryLocationIdsForRole(row.roleCode) : [],
    name: row.name ?? '',
    specialtyCode: row.specialtyCode,
    typeCode: row.typeCode ?? undefined,
    serviceIds: (row.serviceIds ?? []).map(String),
  };
  if (!inventory) {
    await loadLocationOptionsForEdit();
  }
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
  const editingRow = rows.value.find((row) => row.assignmentId === id);
  const roleId = editForm.value.roleId;
  const roleCode = editingRow?.roleCode;
  const orgId = toNullableOrgId(String(editForm.value.orgId ?? ''));
  const locId = toNullableLocationId(editForm.value.locationId);
  const inventoryLocIds = normalizeLocationIdList(editForm.value.locationIds);

  if (
    !usesInventoryLocation(roleId, roleCode) &&
    orgId === undefined &&
    (locId === undefined || locId === null)
  ) {
    ElMessage.warning('科室与位置不可同时为空');
    return;
  }

  const payload: EditPractitionerRoleItemPayload = {
    assignmentId: id,
    name: editForm.value.name?.trim() || undefined,
    specialtyCode: editForm.value.specialtyCode?.trim() || undefined,
    typeCode:
      editForm.value.typeCode === undefined || editForm.value.typeCode === null
        ? undefined
        : Number(editForm.value.typeCode),
  };

  if (usesInventoryLocation(roleId, roleCode)) {
    payload.locationId = inventoryLocIds.join(',');
  } else {
    payload.orgId = orgId ?? null;
    payload.locationId = locId ?? null;
  }

  if (isDoctorAssignment(editForm.value.roleId, editingRow?.roleCode)) {
    payload.serviceIds = [...(editForm.value.serviceIds ?? [])];
  }
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
    await retirePractitionerRoleApi(id, '工作台废弃');
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
      await deactivatePractitionerRoleApi(id);
      ElMessage.success('已停用');
    } else {
      await activatePractitionerRoleApi(id);
      ElMessage.success('已启用');
    }
    await loadAssignments();
  } catch {
    /* 全局拦截 */
  }
}

const hasPractitioner = computed(() => !!preserveApiIdForRequest(props.practitionerId));

/** 平台 admin 账号通常不做业务岗位维护 */
const isPlatformAdminUser = computed(() => (props.userName ?? '').trim().toLowerCase() === 'admin');

/** 左侧已展示系统角色，但尚未写入 adm_pr_assign 时用于空态提示 */
const hasSystemRoles = computed(() => !!(props.roleNames ?? '').trim());

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
  businessRoleOptions.value = await loadBusinessBuiltInRoleOptions();
  if (!addForm.value.roleId && businessRoleOptions.value[0]?.id) {
    addForm.value.roleId = String(businessRoleOptions.value[0].id);
  }
  await loadOrgOptions();
  await loadRegistrationTypeOptions();
  await loadInventoryLocationOptions();
  await loadAssignments();
});
</script>

<template>
  <section class="position-assignments-panel">
    <div class="position-assignments-panel__toolbar">
      <SectionTitle title="岗位分配" />
      <div v-if="hasPractitioner" class="position-assignments-panel__toolbar-actions">
        <ElButton type="primary" @click="showAddForm = !showAddForm">
          {{ showAddForm ? '收起新增' : '新增岗位' }}
        </ElButton>
        <ElButton :loading="loading" @click="loadAssignments">刷新</ElButton>
      </div>
    </div>

    <ElAlert
      v-if="!hasPractitioner"
      show-icon
      :title="isPlatformAdminUser ? '平台管理员未建立医务人员主档' : '未绑定医务人员'"
      type="warning"
      :closable="false"
    >
      <template v-if="isPlatformAdminUser">
        admin
        为平台管理账号，通常无需分配业务岗位。若需为其配置医师/药师等业务身份，请先在「用户管理」中编辑该用户并保存（填写性别等基本信息，将自动创建医务人员主档），再回到本页维护具体岗位。
      </template>
      <template v-else>
        左侧「系统角色」仅控制登录权限，不能代替下方业务岗位。请先在「用户管理」中编辑该用户并保存（需填写性别等基本信息以建立医务人员主档），再在此维护科室/位置等岗位分配。
        <span v-if="hasSystemRoles" class="position-assignments-panel__alert-em">
          当前用户已有系统角色（{{ roleNames }}），但仍需完成上述步骤后才能在下方新增岗位。
        </span>
      </template>
    </ElAlert>

    <template v-else>
      <div v-if="showAddForm" class="position-assignments-panel__add-card">
        <ElForm
          class="position-assignments-panel__add-form"
          label-width="88px"
          @submit.prevent="submitAdd"
        >
          <ElFormItem label="业务角色" required>
            <ElSelect v-model="addForm.roleId" placeholder="请选择角色" @change="onAddRoleChange">
              <ElOption
                v-for="opt in businessRoleOptions"
                :key="opt.id"
                :label="opt.displayName"
                :value="String(opt.id)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="usesOrgLocation(addForm.roleId)" label="科室">
            <ElSelect
              v-model="addForm.orgId"
              clearable
              filterable
              placeholder="请选择机构/科室"
              @change="onAddOrgChange"
            >
              <ElOption v-for="o in orgOptions" :key="o.value" :label="o.label" :value="o.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="usesOrgLocation(addForm.roleId)" label="位置">
            <ElSelect
              v-model="addForm.locationId"
              clearable
              filterable
              :disabled="!addForm.orgId"
              placeholder="请先选择科室"
            >
              <ElOption
                v-for="o in locationOptionsForAdd"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="usesInventoryLocation(addForm.roleId)" label="库房/药房">
            <ElSelect
              v-model="addForm.locationIds"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="请选择库房/药房"
            >
              <ElOption
                v-for="opt in inventoryLocationOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="岗位名称">
            <ElInput v-model="addForm.name" clearable placeholder="可选" />
          </ElFormItem>
          <ElFormItem v-if="isDoctorAssignment(addForm.roleId)" label="号源类型">
            <ElSelect
              v-model="addForm.serviceIds"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="请选择可接诊号源"
              class="w-full"
            >
              <ElOption
                v-for="opt in registrationTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem class="position-assignments-panel__add-actions">
            <ElButton type="primary" @click="submitAdd">保存</ElButton>
            <ElButton @click="resetAddForm">取消</ElButton>
          </ElFormItem>
        </ElForm>
      </div>

      <div class="position-assignments-panel__table-wrap">
        <ElTable
          v-loading="loading"
          :data="rows"
          border
          row-key="assignmentId"
          size="default"
          stripe
        >
          <template #empty>
            <div class="position-assignments-panel__empty">
              <p class="position-assignments-panel__empty-title">暂无业务岗位分配</p>
              <p class="position-assignments-panel__empty-hint">
                业务岗位（科室、位置、库房/药房范围等）保存在独立数据中，与用户管理里勾选的「系统角色」不会自动同步。
              </p>
              <p v-if="hasSystemRoles" class="position-assignments-panel__empty-hint">
                该用户已有系统角色「{{
                  roleNames
                }}」。若已在用户管理中填写过出诊科室/号源等，请确认保存成功；否则请点击上方「新增岗位」补充，或在「用户管理」编辑用户时填写对应岗位区块。
              </p>
              <p v-else class="position-assignments-panel__empty-hint">
                请点击上方「新增岗位」为该用户配置业务角色及管辖范围。
              </p>
            </div>
          </template>
          <ElTableColumn label="业务角色" min-width="100">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElSelect v-model="editForm.roleId" disabled class="w-full">
                  <ElOption
                    v-for="opt in businessRoleOptions"
                    :key="opt.id"
                    :label="opt.displayName"
                    :value="String(opt.id)"
                  />
                </ElSelect>
              </template>
              <span v-else>{{ row.roleName ?? legacyRoleCodeToLabel(row.roleCode) }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="科室" min-width="180">
            <template #default="{ row }">
              <template
                v-if="
                  editingId === row.assignmentId && usesOrgLocation(editForm.roleId, row.roleCode)
                "
              >
                <ElSelect
                  v-model="editForm.orgId"
                  clearable
                  filterable
                  placeholder="科室"
                  class="w-full"
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
              <span v-else-if="usesOrgLocation(undefined, row.roleCode)">
                {{ row.orgName ?? '—' }}
              </span>
              <span v-else>—</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="位置" min-width="140">
            <template #default="{ row }">
              <template v-if="editingId === row.assignmentId">
                <ElSelect
                  v-if="usesOrgLocation(editForm.roleId, row.roleCode)"
                  v-model="editForm.locationId"
                  clearable
                  filterable
                  :disabled="!editForm.orgId"
                  placeholder="请先选择科室"
                  class="w-full"
                >
                  <ElOption
                    v-for="o in locationOptionsForEdit"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </ElSelect>
                <ElSelect
                  v-else-if="usesInventoryLocation(editForm.roleId, row.roleCode)"
                  v-model="editForm.locationIds"
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  filterable
                  multiple
                  placeholder="请选择库房/药房"
                  class="w-full"
                >
                  <ElOption
                    v-for="opt in inventoryLocationOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
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

          <ElTableColumn label="号源类型" min-width="160">
            <template #default="{ row }">
              <template
                v-if="
                  editingId === row.assignmentId &&
                  isDoctorAssignment(editForm.roleId, row.roleCode)
                "
              >
                <ElSelect
                  v-model="editForm.serviceIds"
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  filterable
                  multiple
                  placeholder="请选择号源"
                  class="w-full"
                >
                  <ElOption
                    v-for="opt in registrationTypeOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
              </template>
              <span v-else>{{ row.serviceNames?.trim() ? row.serviceNames : '—' }}</span>
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
  </section>
</template>

<style scoped>
.position-assignments-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.position-assignments-panel__toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.position-assignments-panel__toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.position-assignments-panel__add-card {
  flex-shrink: 0;
  padding: 12px 16px 4px;
  margin-bottom: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.position-assignments-panel__add-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.position-assignments-panel__add-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.position-assignments-panel__add-form :deep(.el-select),
.position-assignments-panel__add-form :deep(.el-input) {
  width: 100%;
}

.position-assignments-panel__add-actions {
  grid-column: 1 / -1;
}

.position-assignments-panel__add-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}

.position-assignments-panel__table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.position-assignments-panel__alert-em {
  display: block;
  margin-top: 6px;
  color: var(--el-text-color-regular);
}

.position-assignments-panel__empty {
  padding: 24px 16px;
  text-align: center;
}

.position-assignments-panel__empty-title {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.position-assignments-panel__empty-hint {
  max-width: 520px;
  margin: 0 auto 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
