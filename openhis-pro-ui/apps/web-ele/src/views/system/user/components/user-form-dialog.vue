<script lang="ts" setup>
import type { UploadProps } from 'element-plus';

import type { VbenFormSchema } from '#/adapter/form';
import type { SaveStaffParams, StaffDetailDto, StaffScopeCode } from '#/api/basic/staff';
import type { WardBed } from '#/api/basic/ward-bed';
import type { Warehouse } from '#/api/basic/warehouse';
import type { ResponsibilityDept } from '#/api/core/common';
import type { RoleInfo } from '#/api/user/role';
import type { UserInfo } from '#/api/user/user';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElButton, ElMessage, ElUpload } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { getCodeListApi, getConceptListApi } from '#/api/basic/code';
import {
  BUILTIN_ROLE_STAFF_SCOPES,
  getEditableScopesForCurrentUserApi,
  getStaffDetailApi,
  saveStaffApi,
  STAFF_SCOPE,
} from '#/api/basic/staff';
import { getWardListApi, WARD_BED_FORM_CODE } from '#/api/basic/ward-bed';
import {
  getWarehouseListApi,
  isPharmacyFormEnum,
  isWarehouseFormEnum,
} from '#/api/basic/warehouse';
import { getTreeForResponsibilityDeptApi } from '#/api/core/common';
import { getRegistrationTypeListApi } from '#/api/core/system/registration';
import { getRoleListApi } from '#/api/user/role';
import { getUserRolesApi } from '#/api/user/user';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { resolveRequestErrorMessage } from '#/utils/http/resolve-request-error-message';
import { GENDER_OPTIONS } from '#/views/inpatient/charge/register/inpatient-register.constants';

interface Props {
  modelValue: boolean;
  user?: null | UserInfo;
}

interface TreeOption {
  label: string;
  value: string;
  children?: TreeOption[];
}

interface SelectOption {
  label: string;
  value: string;
}

/** 用户扩展资料，暂存于 extraProperties，待后端字段对齐后迁移 */
interface UserExtraProfile {
  genderEnum?: string;
  birthDate?: string;
  address?: string;
  ybNo?: string;
  drProfttlCode?: string;
  signatureImage?: string;
  pharPracCertNo?: string;
  consultDeptIds?: string[];
  manageWarehouseIds?: string[];
  managePharmacyIds?: string[];
  manageWardIds?: string[];
  doctorServiceIds?: string[];
  /** @deprecated 兼容旧 extraProperties 单选字段 */
  consultDeptId?: string;
  manageWarehouseId?: string;
  managePharmacyId?: string;
  manageWardId?: string;
  remark?: string;
}

interface ConceptRow {
  code?: string;
  display?: string;
  isActive?: boolean;
  name?: string;
}

interface CodeSystemRow {
  id?: string;
  name?: string;
}

const props = withDefaults(defineProps<Props>(), {
  user: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const isEdit = computed(() => !!props.user);

const isAdminAccount = computed(
  () => (props.user?.userName ?? '').trim().toLowerCase() === 'admin',
);

const originalData = ref<Record<string, unknown>>({});

const allRoles = ref<RoleInfo[]>([]);
const selectedRoleIds = ref<string[]>([]);

const departmentTreeOptions = ref<TreeOption[]>([]);
const warehouseOptions = ref<SelectOption[]>([]);
const pharmacyOptions = ref<SelectOption[]>([]);
const wardOptions = ref<SelectOption[]>([]);
const drProfttlOptions = ref<SelectOption[]>([]);
const registrationTypeOptions = ref<SelectOption[]>([]);

/** 当前操作者可编辑的人员维护块（StaffScope 并集） */
const editableScopes = ref<StaffScopeCode[]>([]);

/** 动态岗位 schema 挂载完成前暂存回显数据，避免二次打开编辑丢值 */
const pendingStaffFormEcho = ref<Record<string, unknown> | null>(null);

/** 批量回显进行中，避免 watch 重复触发 */
const isApplyingStaffFormEcho = ref(false);

let staffEchoApplyToken = 0;
let formOptionsLoadPromise: Promise<void> | null = null;
let editableScopesLoadPromise: Promise<void> | null = null;
const formOptionsReady = ref(false);
const editableScopesReady = ref(false);

const signaturePreview = ref('');
const MAX_SIGNATURE_BYTES = 200 * 1024;

/** 用户表单多选（角色、岗位管辖等）：折叠前展示的标签个数 */
const USER_FORM_MULTI_SELECT_COLLAPSE_PROPS = {
  collapseTags: true,
  collapseTagsTooltip: true,
  maxCollapseTags: 6,
} as const;

/** 将科室树转为 ApiTreeSelect 选项 */
function toTreeOptions<T extends { children?: T[]; id: string; name: string }>(
  list: T[] | undefined,
): TreeOption[] {
  if (!list?.length) {
    return [];
  }
  return list.map((item) => ({
    label: item.name,
    value: String(item.id),
    children: item.children?.length ? toTreeOptions(item.children) : undefined,
  }));
}

/** 解析用户 extraProperties 为结构化扩展字段 */
function parseExtraProperties(
  raw: null | Record<string, unknown> | string | undefined,
): UserExtraProfile {
  if (!raw) {
    return {};
  }
  let parsed: Record<string, unknown> = {};
  if (typeof raw === 'string') {
    try {
      parsed = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return {};
    }
  } else if (typeof raw === 'object') {
    parsed = raw;
  }
  return {
    genderEnum: parsed.genderEnum ? String(parsed.genderEnum) : undefined,
    birthDate: parsed.birthDate ? String(parsed.birthDate) : undefined,
    address: parsed.address ? String(parsed.address) : undefined,
    ybNo: parsed.ybNo ? String(parsed.ybNo) : undefined,
    drProfttlCode: parsed.drProfttlCode ? String(parsed.drProfttlCode) : undefined,
    signatureImage: parsed.signatureImage ? String(parsed.signatureImage) : undefined,
    pharPracCertNo: parsed.pharPracCertNo ? String(parsed.pharPracCertNo) : undefined,
    consultDeptIds: Array.isArray(parsed.consultDeptIds)
      ? parsed.consultDeptIds.map(String)
      : parsed.consultDeptId
        ? [String(parsed.consultDeptId)]
        : [],
    manageWarehouseIds: Array.isArray(parsed.manageWarehouseIds)
      ? parsed.manageWarehouseIds.map(String)
      : parsed.manageWarehouseId
        ? [String(parsed.manageWarehouseId)]
        : [],
    managePharmacyIds: Array.isArray(parsed.managePharmacyIds)
      ? parsed.managePharmacyIds.map(String)
      : parsed.managePharmacyId
        ? [String(parsed.managePharmacyId)]
        : [],
    manageWardIds: Array.isArray(parsed.manageWardIds)
      ? parsed.manageWardIds.map(String)
      : parsed.manageWardId
        ? [String(parsed.manageWardId)]
        : [],
    doctorServiceIds: Array.isArray(parsed.doctorServiceIds)
      ? parsed.doctorServiceIds.map(String)
      : [],
    remark: parsed.remark ? String(parsed.remark) : undefined,
  };
}

/** 从表单值组装 extraProperties JSON 字符串（岗位六块与 gender 已由 saveStaff 维护，不再写入） */
function buildExtraProperties(formValues: Record<string, unknown>): string | undefined {
  const payload: UserExtraProfile = {
    birthDate: formValues.birthDate ? String(formValues.birthDate) : undefined,
    address: formValues.address ? String(formValues.address).trim() : undefined,
    signatureImage: formValues.signatureImage
      ? String(formValues.signatureImage).trim()
      : undefined,
    remark: formValues.remark ? String(formValues.remark).trim() : undefined,
  };

  const hasValue = Object.values(payload).some((value) => {
    if (Array.isArray(value)) {
      return value.length > 0;
    }
    return value !== undefined && value !== '';
  });

  return hasValue ? JSON.stringify(payload) : undefined;
}

/** 解析平台管理员角色 id（admin 账号须始终保留） */
function resolveAdminRoleId(): string | undefined {
  const role = allRoles.value.find(
    (item) =>
      item.code?.trim().toUpperCase() === 'ROLE_ADMIN' ||
      item.name?.trim().toLowerCase() === 'admin',
  );
  return role ? roleIdFromRole(role) : undefined;
}

/** 确保 admin 账号的角色列表始终包含管理员角色 */
function ensureAdminRoleIncluded(roleIds: string[]): string[] {
  const adminRoleId = resolveAdminRoleId();
  if (!adminRoleId) {
    return roleIds;
  }
  const normalized = normalizeIdArray(roleIds);
  if (normalized.includes(adminRoleId)) {
    return normalized;
  }
  return [adminRoleId, ...normalized];
}

/** 按角色 id 解析内置业务 code（兼容 code/name 缺失场景） */
function resolveBuiltinRoleCode(roleId: string): string | undefined {
  const role = allRoles.value.find((item) => roleIdFromRole(item) === roleId);
  if (!role) {
    return undefined;
  }
  const code = role.code?.trim().toUpperCase();
  if (code && BUILTIN_ROLE_STAFF_SCOPES[code]) {
    return code;
  }
  const name = role.name?.trim().toUpperCase();
  if (name && BUILTIN_ROLE_STAFF_SCOPES[name]) {
    return name;
  }
  return code || name;
}

/** 根据已选角色 code 汇总需展示的岗位维护块 */
const activeStaffScopes = computed(() => {
  const scopes = new Set<StaffScopeCode>();
  for (const roleId of selectedRoleIds.value) {
    const code = resolveBuiltinRoleCode(roleId);
    if (!code) {
      continue;
    }
    const mapped = BUILTIN_ROLE_STAFF_SCOPES[code];
    if (mapped) {
      for (const scope of mapped) {
        scopes.add(scope);
      }
    }
  }
  return [...scopes];
});

/** 已选角色是否关联某岗位块 */
function roleScopeLinked(scope: StaffScopeCode): boolean {
  return activeStaffScopes.value.includes(scope);
}

/** 当前操作者是否可写某岗位块（用户管理内随所选角色联动即可编辑） */
function scopeWritable(scope: StaffScopeCode): boolean {
  if (!roleScopeLinked(scope)) {
    return false;
  }
  if (editableScopes.value.length === 0) {
    return true;
  }
  return editableScopes.value.includes(scope);
}

/** 将 orgId 数组转为岗位维护列表项 */
function orgListFromIds(ids: unknown): { orgId?: string }[] {
  if (!Array.isArray(ids)) {
    return [];
  }
  return ids
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => !!id)
    .map((orgId) => ({ orgId }));
}

/** 将 locationId 多选转为岗位维护列表项 */
function locationListFromIds(ids: unknown): { locationId?: string }[] {
  if (!Array.isArray(ids)) {
    return [];
  }
  return ids
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => !!id)
    .map((locationId) => ({ locationId }));
}

/** 将接口返回的 id 列表规范为 string[]（回显/提交一致） */
function normalizeIdArray(raw: unknown): string[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((id) => preserveApiIdForRequest(id)).filter((id): id is string => !!id);
}

/** 从岗位详情列表提取 orgId 数组 */
function mapOrgIdsFromList(list?: { orgId?: string | number }[]): string[] {
  return normalizeIdArray((list ?? []).map((item) => item.orgId));
}

/** 从岗位详情列表提取 locationId 数组 */
function mapLocationIdsFromList(list?: { locationId?: string | number }[]): string[] {
  return normalizeIdArray((list ?? []).map((item) => item.locationId));
}

/** 收集树节点全部 value，用于判断回显 id 是否已在选项中 */
function collectTreeValues(nodes: TreeOption[]): Set<string> {
  const values = new Set<string>();
  const walk = (list: TreeOption[]) => {
    for (const node of list) {
      values.add(node.value);
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };
  walk(nodes);
  return values;
}

/** 为树形下拉补齐已保存但不在树中的节点，避免编辑不回显 */
function mergeOrphanIdsIntoTree(tree: TreeOption[], ids: string[]): TreeOption[] {
  const existing = collectTreeValues(tree);
  const orphans = ids
    .filter((id) => id && !existing.has(id))
    .map((id) => ({ label: `科室（${id}）`, value: id }));
  return orphans.length ? [...orphans, ...tree] : tree;
}

/** 为普通下拉补齐已保存但不在选项中的值 */
function mergeSelectOptionsWithMissingValues(
  options: SelectOption[],
  values: string[],
  labelPrefix: string,
): SelectOption[] {
  const existing = new Set(options.map((item) => item.value));
  const merged = [...options];
  for (const value of values) {
    if (value && !existing.has(value)) {
      merged.push({ label: `${labelPrefix}（${value}）`, value });
    }
  }
  return merged;
}

/** 加载当前用户可维护的 StaffScope */
async function loadEditableScopes() {
  try {
    const res = await getEditableScopesForCurrentUserApi();
    const data = (res as { editableScopes?: StaffScopeCode[] }).editableScopes ?? res;
    editableScopes.value = Array.isArray(data) ? data : [];
  } catch {
    editableScopes.value = [];
  }
}

/** 从表单值组装 saveStaff 请求体 */
function buildSaveStaffParams(
  formValues: Record<string, unknown>,
  userId?: string,
): SaveStaffParams {
  const roleIds = Array.isArray(formValues.roleIds)
    ? formValues.roleIds.map((id) => preserveApiIdForRequest(id)).filter((id): id is string => !!id)
    : [];

  const params: SaveStaffParams = {
    userId: userId ? preserveApiIdForRequest(userId) : undefined,
    userName: formValues.userName ? String(formValues.userName) : undefined,
    password: formValues.password ? String(formValues.password) : undefined,
    name: formValues.name ? String(formValues.name) : undefined,
    email: formValues.email ? String(formValues.email) : undefined,
    phoneNumber: formValues.phoneNumber ? String(formValues.phoneNumber) : undefined,
    isActive: formValues.isActive === undefined ? true : String(formValues.isActive) === 'true',
    roleIds,
    genderEnum: formValues.genderEnum ? String(formValues.genderEnum) : undefined,
    ybNo: formValues.ybNo ? String(formValues.ybNo).trim() : '',
    drProfttlCode: formValues.drProfttlCode ? String(formValues.drProfttlCode) : undefined,
    pharPracCertNo: formValues.pharPracCertNo ? String(formValues.pharPracCertNo).trim() : '',
    extraProperties: buildExtraProperties(formValues),
  };

  if (
    roleScopeLinked(STAFF_SCOPE.DOCTOR_VISIT_ORG) &&
    scopeWritable(STAFF_SCOPE.DOCTOR_VISIT_ORG)
  ) {
    params.doctorVisitOrgList = orgListFromIds(formValues.consultDeptIds);
    const serviceIds = Array.isArray(formValues.doctorServiceIds)
      ? formValues.doctorServiceIds
          .map((id) => preserveApiIdForRequest(id))
          .filter((id): id is string => !!id)
      : [];
    params.doctorServiceIds = serviceIds;
  }
  if (
    roleScopeLinked(STAFF_SCOPE.WAREHOUSE_LOCATION) &&
    scopeWritable(STAFF_SCOPE.WAREHOUSE_LOCATION)
  ) {
    params.warehouseLocationList = locationListFromIds(formValues.manageWarehouseIds);
  }
  if (
    roleScopeLinked(STAFF_SCOPE.PHARMACY_LOCATION) &&
    scopeWritable(STAFF_SCOPE.PHARMACY_LOCATION)
  ) {
    params.pharmacyLocationList = locationListFromIds(formValues.managePharmacyIds);
  }
  if (
    roleScopeLinked(STAFF_SCOPE.NURSE_WARD_LOCATION) &&
    scopeWritable(STAFF_SCOPE.NURSE_WARD_LOCATION)
  ) {
    params.nurseWardLocationList = locationListFromIds(formValues.manageWardIds);
  }

  const defaultLoginOrgId = formValues.defaultLoginOrgId;
  if (
    defaultLoginOrgId !== undefined &&
    defaultLoginOrgId !== null &&
    String(defaultLoginOrgId).trim() !== ''
  ) {
    params.syncPrimaryOrgId = true;
    params.primaryOrgId = preserveApiIdForRequest(defaultLoginOrgId) ?? String(defaultLoginOrgId);
  }

  return params;
}

/** 将岗位详情合并进表单默认值 */
function mergeStaffDetailIntoForm(
  detail: StaffDetailDto,
  extra: UserExtraProfile,
  roleIds: string[],
) {
  return {
    ...buildDefaultFormValues(extra, roleIds),
    userName: detail.userName ?? props.user?.userName ?? '',
    name: detail.name ?? props.user?.name ?? '',
    phoneNumber: detail.phoneNumber ?? props.user?.phoneNumber ?? '',
    email: detail.email ?? props.user?.email ?? '',
    isActive: detail.isActive === undefined ? 'true' : String(detail.isActive),
    genderEnum: detail.genderEnum ?? extra.genderEnum ?? '',
    ybNo: detail.ybNo ?? extra.ybNo ?? '',
    drProfttlCode:
      detail.drProfttlCode != null && String(detail.drProfttlCode) !== ''
        ? String(detail.drProfttlCode)
        : (extra.drProfttlCode ?? ''),
    pharPracCertNo: detail.pharPracCertNo ?? extra.pharPracCertNo ?? '',
    roleIds,
    consultDeptIds: mapOrgIdsFromList(detail.doctorVisitOrgList).length
      ? mapOrgIdsFromList(detail.doctorVisitOrgList)
      : (extra.consultDeptIds ?? []),
    manageWarehouseIds: mapLocationIdsFromList(detail.warehouseLocationList).length
      ? mapLocationIdsFromList(detail.warehouseLocationList)
      : (extra.manageWarehouseIds ?? []),
    managePharmacyIds: mapLocationIdsFromList(detail.pharmacyLocationList).length
      ? mapLocationIdsFromList(detail.pharmacyLocationList)
      : (extra.managePharmacyIds ?? []),
    manageWardIds: mapLocationIdsFromList(detail.nurseWardLocationList).length
      ? mapLocationIdsFromList(detail.nurseWardLocationList)
      : normalizeIdArray(extra.manageWardIds),
    doctorServiceIds: normalizeIdArray(detail.doctorServiceIds ?? extra.doctorServiceIds),
    defaultLoginOrgId: detail.primaryOrgId ? String(detail.primaryOrgId) : '',
  };
}

/** 从角色对象提取请求体/下拉一致的 id 字符串 */
function roleIdFromRole(role: Pick<RoleInfo, 'id'> | { id?: unknown }): string | undefined {
  return preserveApiIdForRequest(role.id);
}

/** 合并多组角色列表为去重 id 数组 */
function resolveRoleIds(...roleLists: Array<RoleInfo[] | undefined>): string[] {
  const ids = new Set<string>();
  for (const list of roleLists) {
    for (const role of list ?? []) {
      const id = roleIdFromRole(role);
      if (id) {
        ids.add(id);
      }
    }
  }
  return [...ids];
}

/** 将已分配角色补进下拉选项，避免选项未加载时无法回显 */
function mergeAssignedRolesIntoOptions(assignedRoles: RoleInfo[]) {
  if (!assignedRoles.length) {
    return;
  }
  const byId = new Map(
    allRoles.value
      .map((role) => [roleIdFromRole(role), role] as const)
      .filter((entry): entry is [string, RoleInfo] => !!entry[0]),
  );
  for (const assigned of assignedRoles) {
    const id = roleIdFromRole(assigned);
    if (!id) {
      continue;
    }
    const existing = byId.get(id);
    if (existing) {
      if (!assigned.code && existing.code) {
        assigned.code = existing.code;
      }
      if (!assigned.name && existing.name) {
        assigned.name = existing.name;
      }
      continue;
    }
    byId.set(id, assigned);
  }
  allRoles.value = [...byId.values()];
}

/** 加载角色列表 */
const loadRoles = async () => {
  try {
    const response = await getRoleListApi({
      pageNo: 1,
      pageSize: 1000,
    });
    allRoles.value = response.content || [];
  } catch (error) {
    console.error('加载角色列表失败:', error);
    allRoles.value = [];
  }
};

/** 查询用户已绑定的系统角色 */
async function loadUserAssignedRoles(userId: string): Promise<RoleInfo[]> {
  try {
    const rolesResponse = await getUserRolesApi({
      value: preserveApiIdForRequest(userId) ?? userId,
    });
    return Array.isArray(rolesResponse) ? rolesResponse : [];
  } catch (error) {
    console.error('加载用户角色失败:', error);
    return [];
  }
}

const POSITION_FIELD_NAMES = [
  'consultDeptIds',
  'doctorServiceIds',
  'manageWarehouseIds',
  'managePharmacyIds',
  'manageWardIds',
] as const;

/** 岗位多选字段二次赋值：动态 schema 挂载后须再写一遍才能回显 */
async function applyPositionFieldsToForm(formData: Record<string, unknown>) {
  const patch: Record<string, unknown> = {
    defaultLoginOrgId:
      formData.defaultLoginOrgId !== undefined && formData.defaultLoginOrgId !== null
        ? String(formData.defaultLoginOrgId)
        : '',
  };
  for (const fieldName of POSITION_FIELD_NAMES) {
    patch[fieldName] = [...normalizeIdArray(formData[fieldName])];
  }
  await formApi.setValues(patch);
}

/** 为编辑回显补齐下拉/树选项中缺失的已保存值 */
function ensureEchoOptionsForStaffForm(formData: Record<string, unknown>) {
  const consultDeptIds = normalizeIdArray(formData.consultDeptIds);
  const defaultLoginOrgIds =
    formData.defaultLoginOrgId !== undefined &&
    formData.defaultLoginOrgId !== null &&
    String(formData.defaultLoginOrgId).trim() !== ''
      ? [String(formData.defaultLoginOrgId)]
      : [];
  const manageWarehouseIds = normalizeIdArray(formData.manageWarehouseIds);
  const managePharmacyIds = normalizeIdArray(formData.managePharmacyIds);
  const manageWardIds = normalizeIdArray(formData.manageWardIds);
  const doctorServiceIds = normalizeIdArray(formData.doctorServiceIds);

  departmentTreeOptions.value = mergeOrphanIdsIntoTree(departmentTreeOptions.value, [
    ...consultDeptIds,
    ...defaultLoginOrgIds,
  ]);
  warehouseOptions.value = mergeSelectOptionsWithMissingValues(
    warehouseOptions.value,
    manageWarehouseIds,
    '库房',
  );
  pharmacyOptions.value = mergeSelectOptionsWithMissingValues(
    pharmacyOptions.value,
    managePharmacyIds,
    '药房',
  );
  wardOptions.value = mergeSelectOptionsWithMissingValues(wardOptions.value, manageWardIds, '病区');
  registrationTypeOptions.value = mergeSelectOptionsWithMissingValues(
    registrationTypeOptions.value,
    doctorServiceIds,
    '号源',
  );
}

function normalizeStaffFormEcho(formData: Record<string, unknown>) {
  const roleIds = normalizeIdArray(formData.roleIds);
  return {
    ...formData,
    roleIds,
    defaultLoginOrgId:
      formData.defaultLoginOrgId !== undefined && formData.defaultLoginOrgId !== null
        ? String(formData.defaultLoginOrgId)
        : '',
    consultDeptIds: normalizeIdArray(formData.consultDeptIds),
    manageWarehouseIds: normalizeIdArray(formData.manageWarehouseIds),
    managePharmacyIds: normalizeIdArray(formData.managePharmacyIds),
    manageWardIds: normalizeIdArray(formData.manageWardIds),
    doctorServiceIds: normalizeIdArray(formData.doctorServiceIds),
  };
}

/** 统一写入表单：先挂载角色联动字段，再整体赋值并二次回显岗位块 */
async function applyStaffFormValues(formData: Record<string, unknown>) {
  const token = ++staffEchoApplyToken;
  isApplyingStaffFormEcho.value = true;
  try {
    ensureEchoOptionsForStaffForm(formData);
    pendingStaffFormEcho.value = { ...formData };

    const normalizedFormData = normalizeStaffFormEcho(formData);
    selectedRoleIds.value = normalizedFormData.roleIds;
    await nextTick();

    if (token !== staffEchoApplyToken) {
      return;
    }

    await formApi.setValues(normalizedFormData);
    await nextTick();

    if (token !== staffEchoApplyToken) {
      return;
    }

    await applyPositionFieldsToForm(normalizedFormData);
  } finally {
    isApplyingStaffFormEcho.value = false;
  }
}

/** 岗位块 schema 变更后，用暂存数据补写岗位多选/树形回显（不覆盖用户已选角色） */
async function reapplyPendingStaffFormEcho() {
  if (isApplyingStaffFormEcho.value) {
    return;
  }
  const pending = pendingStaffFormEcho.value;
  if (!pending || !props.modelValue) {
    return;
  }
  const normalized = normalizeStaffFormEcho(pending);
  await nextTick();
  await applyPositionFieldsToForm(normalized);
}

/** 加载出诊科室树（排除医院根节点，仅含已维护科室分类的机构） */
async function loadStaffDeptTrees() {
  try {
    const data = unwrapApiList<ResponsibilityDept>(await getTreeForResponsibilityDeptApi());
    departmentTreeOptions.value = toTreeOptions(data);
  } catch (error) {
    console.error('加载科室树失败:', error);
    departmentTreeOptions.value = [];
  }
}

/** 加载药库/药房选项 */
async function loadWarehouseOptions() {
  try {
    const rows = unwrapApiList<Warehouse>(await getWarehouseListApi());
    warehouseOptions.value = rows
      .filter((item) => isWarehouseFormEnum(item.formEnum))
      .map((item) => ({ label: item.name, value: String(item.id) }));
    pharmacyOptions.value = rows
      .filter((item) => isPharmacyFormEnum(item.formEnum))
      .map((item) => ({ label: item.name, value: String(item.id) }));
  } catch (error) {
    console.error('加载药库/药房选项失败:', error);
    warehouseOptions.value = [];
    pharmacyOptions.value = [];
  }
}

/** 加载号源类型选项（医生岗位） */
async function loadRegistrationTypeOptions() {
  try {
    const list = await getRegistrationTypeListApi();
    registrationTypeOptions.value = (list ?? []).map((item) => ({
      label: item.name,
      value: String(item.id),
    }));
  } catch (error) {
    console.error('加载号源类型失败:', error);
    registrationTypeOptions.value = [];
  }
}

/** 加载病区选项 */
async function loadWardOptions() {
  try {
    const rows = unwrapApiList<WardBed>(await getWardListApi());
    wardOptions.value = rows
      .filter((item) => item.formEnum === WARD_BED_FORM_CODE.WARD)
      .map((item) => ({ label: String(item.name ?? ''), value: String(item.id ?? '') }))
      .filter((item) => item.value);
  } catch (error) {
    console.error('加载病区选项失败:', error);
    wardOptions.value = [];
  }
}

/** 从代码系统加载职称编码选项 */
async function loadDrProfttlOptions() {
  try {
    const response = await getCodeListApi({ pageNo: 1, pageSize: 200, name: 'drord_dr_profttl' });
    const systems = Array.isArray(response)
      ? response
      : ((response as { content?: CodeSystemRow[] }).content ?? []);
    const system = systems.find((item) => item.name === 'drord_dr_profttl');
    if (!system?.id) {
      drProfttlOptions.value = [];
      return;
    }
    const conceptResponse = await getConceptListApi({ id: system.id });
    const concepts = Array.isArray(conceptResponse)
      ? conceptResponse
      : ((conceptResponse as { data?: ConceptRow[] }).data ?? []);
    drProfttlOptions.value = concepts
      .filter((item) => item.isActive !== false)
      .map((item) => {
        const value = String(item.code ?? '').trim();
        const label = String(item.display ?? item.name ?? value).trim() || value;
        return { label, value };
      })
      .filter((item) => item.value);
  } catch (error) {
    console.error('加载职称编码失败:', error);
    drProfttlOptions.value = [];
  }
}

/** 并行加载表单下拉/树形选项（仅首次） */
async function loadFormOptions() {
  await Promise.all([
    loadRoles(),
    loadStaffDeptTrees(),
    loadWarehouseOptions(),
    loadWardOptions(),
    loadDrProfttlOptions(),
    loadRegistrationTypeOptions(),
  ]);
  formOptionsReady.value = true;
}

async function ensureFormOptionsLoaded() {
  if (formOptionsReady.value) {
    return;
  }
  if (!formOptionsLoadPromise) {
    formOptionsLoadPromise = loadFormOptions().finally(() => {
      formOptionsLoadPromise = null;
    });
  }
  await formOptionsLoadPromise;
}

async function ensureEditableScopesLoaded() {
  if (editableScopesReady.value) {
    return;
  }
  if (!editableScopesLoadPromise) {
    editableScopesLoadPromise = loadEditableScopes()
      .then(() => {
        editableScopesReady.value = true;
      })
      .finally(() => {
        editableScopesLoadPromise = null;
      });
  }
  await editableScopesLoadPromise;
}

/** 先用列表行数据占位，详情返回后再合并岗位字段 */
async function applyBasicUserFormValues(extra: UserExtraProfile, roleIds: string[] = []) {
  await formApi.setValues(buildDefaultFormValues(extra, roleIds));
}

async function prefetchFormResources() {
  await Promise.all([ensureFormOptionsLoaded(), ensureEditableScopesLoaded()]);
}

const roleOptions = computed(() => {
  return allRoles.value
    .map((role) => {
      const value = roleIdFromRole(role);
      if (!value) {
        return null;
      }
      return {
        label: role.displayName || role.name,
        value,
      };
    })
    .filter((item): item is SelectOption => !!item);
});

const genderOptions = computed(() =>
  GENDER_OPTIONS.map((item) => ({
    label: item.label.replace('性', ''),
    value: item.value,
  })),
);

/** 读取本地图片为 data URL 并写入表单 */
function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(String(reader.result ?? '')));
    reader.addEventListener('error', () => reject(new Error('读取图片失败')));
    reader.readAsDataURL(file);
  });
}

/** 上传签名图前校验并预览 */
const beforeSignatureUpload: UploadProps['beforeUpload'] = async (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请上传 PNG、JPG 等图片格式');
    return false;
  }
  if (rawFile.size > MAX_SIGNATURE_BYTES) {
    ElMessage.warning('签名图片请小于 200KB');
    return false;
  }
  try {
    const dataUrl = await fileToDataUrl(rawFile);
    signaturePreview.value = dataUrl;
    await formApi.setFieldValue('signatureImage', dataUrl);
  } catch {
    ElMessage.error('读取图片失败');
  }
  return false;
};

/** 清除已选签名图 */
async function handleClearSignature() {
  signaturePreview.value = '';
  await formApi.setFieldValue('signatureImage', '');
}

/** 提交新增/编辑用户（用户 + 岗位 + 扩展资料一体 saveStaff） */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = (await formApi.getValues()) as Record<string, unknown>;
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };

    let roleIds = normalizeIdArray(mergedData.roleIds);
    if (isAdminAccount.value) {
      roleIds = ensureAdminRoleIncluded(roleIds);
      mergedData.roleIds = roleIds;
    }
    selectedRoleIds.value = roleIds;

    const saveParams = buildSaveStaffParams(
      mergedData,
      isEdit.value ? preserveApiIdForRequest(props.user?.id) : undefined,
    );
    await saveStaffApi(saveParams);

    ElMessage.success(isEdit.value ? '编辑用户成功' : '新增用户成功');
    if (activeStaffScopes.value.some((scope) => scopeWritable(scope))) {
      ElMessage.info('岗位信息已变更，建议重新登录或刷新令牌以使数据管辖生效');
    }
    emit('success');
    editableScopesReady.value = false;
    modalApi.close();
  } catch (error: unknown) {
    ElMessage.error(
      resolveRequestErrorMessage(
        error,
        isEdit.value ? '编辑用户失败，请稍后重试' : '新增用户失败，请稍后重试',
      ),
    );
  } finally {
    modalApi.unlock();
  }
}

/** 组装编辑/新增时的默认表单值 */
function buildDefaultFormValues(extra: UserExtraProfile, roleIds: string[] = []) {
  return {
    userName: props.user?.userName || '',
    name: props.user?.name || '',
    phoneNumber: props.user?.phoneNumber || '',
    email: props.user?.email || '',
    isActive: props.user ? String(props.user.isActive) : 'true',
    roleIds,
    genderEnum: extra.genderEnum ?? '',
    birthDate: extra.birthDate ?? '',
    address: extra.address ?? '',
    ybNo: extra.ybNo ?? '',
    drProfttlCode: extra.drProfttlCode ?? '',
    signatureImage: extra.signatureImage ?? '',
    pharPracCertNo: extra.pharPracCertNo ?? '',
    consultDeptIds: extra.consultDeptIds ?? [],
    manageWarehouseIds: extra.manageWarehouseIds ?? [],
    managePharmacyIds: extra.managePharmacyIds ?? [],
    manageWardIds: extra.manageWardIds ?? [],
    doctorServiceIds: extra.doctorServiceIds ?? [],
    defaultLoginOrgId: '',
    remark: extra.remark ?? '',
  };
}

/** 打开弹窗时加载表单数据 */
async function loadFormData() {
  modalApi.setState({ loading: true });
  try {
    if (props.user?.id) {
      ++staffEchoApplyToken;
      selectedRoleIds.value = [];
      pendingStaffFormEcho.value = null;

      originalData.value = { ...props.user };
      const extra = parseExtraProperties(props.user.extraProperties);
      signaturePreview.value = extra.signatureImage ?? '';
      const userId = preserveApiIdForRequest(props.user.id) ?? String(props.user.id);

      await formApi.resetForm();

      const [staffDetailResult] = await Promise.all([
        getStaffDetailApi(userId).catch((error) => {
          console.error('获取人员详情失败:', error);
          return null;
        }),
        prefetchFormResources(),
        applyBasicUserFormValues(extra, []),
      ]);

      const detailRoles = staffDetailResult?.systemRoles ?? [];
      let assignedRoles: RoleInfo[] = [];
      if (detailRoles.length === 0) {
        assignedRoles = await loadUserAssignedRoles(userId);
      }
      mergeAssignedRolesIntoOptions([...detailRoles, ...assignedRoles]);
      const roleIds = resolveRoleIds(detailRoles, assignedRoles);

      if (staffDetailResult) {
        const formData = mergeStaffDetailIntoForm(staffDetailResult, extra, roleIds);
        await applyStaffFormValues(formData);
        return;
      }

      await applyStaffFormValues(buildDefaultFormValues(extra, roleIds));
    } else {
      await prefetchFormResources();
      originalData.value = {};
      selectedRoleIds.value = [];
      pendingStaffFormEcho.value = null;
      signaturePreview.value = '';
      await formApi.resetForm();
      await formApi.setValues({
        isActive: 'true',
        roleIds: [],
        consultDeptIds: [],
        manageWarehouseIds: [],
        managePharmacyIds: [],
        manageWardIds: [],
        doctorServiceIds: [],
        signatureImage: '',
        defaultLoginOrgId: '',
      });
    }
  } finally {
    modalApi.setState({ loading: false });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:modelValue', isOpen);
  },
  onOpened: async () => {
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const fullWidthStyle = { width: '100%' };
const sectionTitleClass = 'col-span-2 !mb-0 !pb-0 user-form-dialog__section-title';

const formConfig = reactive({
  commonConfig: {
    labelWidth: 100,
    labelClass: 'justify-start text-left',
    componentProps: {
      style: fullWidthStyle,
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'user-form-dialog__grid grid grid-cols-2 gap-x-6',
  schema: computed((): VbenFormSchema[] => {
    const accountFields: VbenFormSchema[] = [];

    if (isEdit.value) {
      accountFields.push({
        component: 'Input',
        fieldName: 'userName',
        label: '用户名',
        disabled: true,
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请输入用户名',
          style: fullWidthStyle,
        },
        rules: z.string().min(1, { message: '请输入用户名' }),
      });
    } else {
      accountFields.push(
        {
          component: 'Input',
          fieldName: 'userName',
          label: '用户名',
          componentProps: {
            placeholder: '请输入用户名',
          },
          rules: z.string().min(1, { message: '请输入用户名' }),
        },
        {
          component: 'Input',
          fieldName: 'password',
          label: '密码',
          componentProps: {
            type: 'password',
            showPassword: true,
            placeholder: '请输入密码（至少8位）',
          },
          rules: z.string().min(6, { message: '密码长度不能少于6位' }),
        },
      );
    }

    const roleField: VbenFormSchema = {
      component: 'Select',
      fieldName: 'roleIds',
      label: '角色',
      formItemClass: 'col-span-2',
      componentProps: {
        placeholder: '请选择角色',
        options: roleOptions.value,
        multiple: true,
        ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
        style: fullWidthStyle,
        onChange: async (value: string[]) => {
          const nextRoleIds = isAdminAccount.value
            ? ensureAdminRoleIncluded(value)
            : normalizeIdArray(value);
          selectedRoleIds.value = nextRoleIds;
          if (pendingStaffFormEcho.value) {
            pendingStaffFormEcho.value = {
              ...pendingStaffFormEcho.value,
              roleIds: [...nextRoleIds],
            };
          }
          if (isAdminAccount.value) {
            await formApi.setFieldValue('roleIds', [...nextRoleIds]);
          }
          await nextTick();
          await reapplyPendingStaffFormEcho();
        },
      },
      rules: z.array(z.string()).min(1, { message: '请选择角色' }),
    };

    const defaultLoginOrgField: VbenFormSchema = {
      component: 'ApiTreeSelect',
      fieldName: 'defaultLoginOrgId',
      label: '默认登录科室',
      formItemClass: 'col-span-2',
      componentProps: {
        data: departmentTreeOptions.value,
        checkStrictly: true,
        filterable: true,
        clearable: true,
        placeholder: '不填则登录时使用当前医院',
        style: fullWidthStyle,
      },
    };

    const positionFields: VbenFormSchema[] = [];

    if (activeStaffScopes.value.length > 0) {
      positionFields.push({
        component: 'SectionTitle',
        fieldName: '_sectionPositionInfo',
        label: '岗位管辖',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: {
          title: '岗位管辖（随所选角色联动）',
        },
      });
    }

    if (roleScopeLinked(STAFF_SCOPE.DOCTOR_VISIT_ORG)) {
      positionFields.push({
        component: 'ApiTreeSelect',
        fieldName: 'consultDeptIds',
        label: '出诊科室',
        formItemClass: 'col-span-2',
        componentProps: {
          data: departmentTreeOptions.value,
          multiple: true,
          checkStrictly: true,
          filterable: true,
          clearable: true,
          ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
          disabled: !scopeWritable(STAFF_SCOPE.DOCTOR_VISIT_ORG),
          placeholder: '请选择出诊科室',
          style: fullWidthStyle,
        },
      });
    }

    if (roleScopeLinked(STAFF_SCOPE.DOCTOR_VISIT_ORG)) {
      positionFields.push({
        component: 'Select',
        fieldName: 'doctorServiceIds',
        label: '可接诊号源',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请选择号源类型',
          options: registrationTypeOptions.value,
          multiple: true,
          ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
          clearable: true,
          filterable: true,
          disabled: !scopeWritable(STAFF_SCOPE.DOCTOR_VISIT_ORG),
          style: fullWidthStyle,
        },
      });
    }

    if (roleScopeLinked(STAFF_SCOPE.WAREHOUSE_LOCATION)) {
      positionFields.push({
        component: 'Select',
        fieldName: 'manageWarehouseIds',
        label: '管理库房',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请选择管理库房',
          clearable: true,
          filterable: true,
          multiple: true,
          ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
          options: warehouseOptions.value,
          disabled: !scopeWritable(STAFF_SCOPE.WAREHOUSE_LOCATION),
          style: fullWidthStyle,
        },
      });
    }

    if (roleScopeLinked(STAFF_SCOPE.PHARMACY_LOCATION)) {
      positionFields.push({
        component: 'Select',
        fieldName: 'managePharmacyIds',
        label: '管理药房',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请选择管理药房',
          clearable: true,
          filterable: true,
          multiple: true,
          ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
          options: pharmacyOptions.value,
          disabled: !scopeWritable(STAFF_SCOPE.PHARMACY_LOCATION),
          style: fullWidthStyle,
        },
      });
    }

    if (roleScopeLinked(STAFF_SCOPE.NURSE_WARD_LOCATION)) {
      positionFields.push({
        component: 'Select',
        fieldName: 'manageWardIds',
        label: '管理病区',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请选择管理病区',
          clearable: true,
          filterable: true,
          multiple: true,
          ...USER_FORM_MULTI_SELECT_COLLAPSE_PROPS,
          options: wardOptions.value,
          disabled: !scopeWritable(STAFF_SCOPE.NURSE_WARD_LOCATION),
          style: fullWidthStyle,
        },
      });
    }

    return [
      ...accountFields,
      roleField,
      defaultLoginOrgField,
      ...positionFields,
      {
        component: 'SectionTitle',
        fieldName: '_sectionUserInfo',
        label: '用户信息',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: {
          title: '用户信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '用户昵称',
        componentProps: {
          placeholder: '请输入用户昵称',
        },
        rules: z.string().min(1, { message: '请输入用户昵称' }),
      },
      {
        component: 'Input',
        fieldName: 'phoneNumber',
        label: '手机号码',
        componentProps: {
          placeholder: '请输入手机号码',
        },
        rules: z
          .string()
          .optional()
          .refine((val) => !val || /^1[3-9]\d{9}$/.test(val), { message: '请输入正确的手机号码' }),
      },
      {
        component: 'Input',
        fieldName: 'email',
        label: '邮箱',
        componentProps: {
          placeholder: '请输入邮箱',
        },
        rules: z
          .string()
          .optional()
          .refine((val) => !val || z.string().email().safeParse(val).success, {
            message: '请输入正确的邮箱地址',
          }),
      },
      {
        component: 'RadioGroup',
        fieldName: 'genderEnum',
        label: '用户性别',
        componentProps: {
          options: genderOptions.value,
        },
        rules: z.string().min(1, { message: '请选择用户性别' }),
      },
      {
        component: 'RadioGroup',
        fieldName: 'isActive',
        label: '状态',
        componentProps: {
          options: [
            { label: '正常', value: 'true' },
            { label: '停用', value: 'false' },
          ],
          disabled: props.user?.userName === 'admin',
        },
        rules: z.string().min(1, { message: '请选择状态' }),
      },
      {
        component: 'DatePicker',
        fieldName: 'birthDate',
        label: '出生日期',
        componentProps: {
          type: 'date',
          valueFormat: 'YYYY-MM-DD',
          placeholder: '选择日期',
          style: fullWidthStyle,
        },
      },
      {
        component: 'Input',
        fieldName: 'address',
        label: '地址',
        componentProps: {
          placeholder: '请输入地址',
        },
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保码',
        componentProps: {
          placeholder: '请输入医保码',
        },
      },
      {
        component: 'custom',
        fieldName: 'signatureImage',
        label: '签名图片',
        formItemClass: 'user-form-dialog__signature-cell row-span-2',
        slot: 'signatureUpload',
      } as VbenFormSchema,
      {
        component: 'Select',
        fieldName: 'drProfttlCode',
        label: '职称编码',
        componentProps: {
          placeholder: '请选择职称编码',
          clearable: true,
          filterable: true,
          options: drProfttlOptions.value,
          style: fullWidthStyle,
        },
      },
      {
        component: 'Input',
        fieldName: 'pharPracCertNo',
        label: '职业资格证编号',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请输入职业资格证编号',
          style: fullWidthStyle,
        },
      },
      {
        component: 'SectionTitle',
        fieldName: '_sectionRemark',
        label: '备注',
        hideLabel: true,
        formItemClass: sectionTitleClass,
        componentProps: {
          title: '备注',
        },
      },
      {
        component: 'InputArea',
        fieldName: 'remark',
        label: '备注',
        hideLabel: true,
        formItemClass: 'col-span-2 user-form-dialog__remark',
        componentProps: {
          rows: 3,
          placeholder: '请输入内容',
          style: fullWidthStyle,
        },
      },
    ];
  }),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
      pendingStaffFormEcho.value = null;
      selectedRoleIds.value = [];
    }
  },
  { immediate: true },
);

/** 岗位块随角色挂载后，用暂存数据补写回显（避免动态 schema 重建冲掉字段值） */
watch(
  () => activeStaffScopes.value.join(','),
  async () => {
    await reapplyPendingStaffFormEcho();
  },
);

onMounted(() => {
  void prefetchFormResources();
});
</script>

<template>
  <Modal
    class="user-form-dialog w-[920px] max-w-[96vw]"
    :title="isEdit ? '编辑用户' : '新增用户'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form>
      <template #signatureUpload>
        <div class="signature-upload-wrap w-full">
          <ElUpload
            class="w-full"
            drag
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp"
            :before-upload="beforeSignatureUpload"
          >
            <div
              class="signature-preview-box flex min-h-[88px] w-full flex-col items-center justify-center gap-1 rounded border border-dashed bg-[var(--el-fill-color-blank)] px-2 py-3"
            >
              <img
                v-if="signaturePreview"
                :src="signaturePreview"
                alt="签名预览"
                class="max-h-14 max-w-[140px] object-contain"
              />
              <span v-else class="text-sm text-[var(--el-color-primary)]">点击上传图片</span>
              <span v-if="!signaturePreview" class="text-muted-foreground text-xs">
                支持 PNG / JPG，小于 200KB
              </span>
            </div>
          </ElUpload>
          <ElButton
            v-if="signaturePreview"
            class="mt-1"
            link
            type="danger"
            @click="handleClearSignature"
          >
            清除签名
          </ElButton>
        </div>
      </template>
    </Form>
  </Modal>
</template>

<style scoped>
.user-form-dialog :deep(.user-form-dialog__grid) {
  align-items: start;
}

.user-form-dialog :deep(.el-form-item) {
  margin-bottom: 16px;
}

.user-form-dialog :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
}

.user-form-dialog :deep(.user-form-dialog__section-title) {
  margin-top: 4px;
  padding-bottom: 0 !important;
}

.user-form-dialog :deep(.user-form-dialog__section-title .el-form-item__content) {
  min-height: 0;
}

.user-form-dialog :deep(.user-form-dialog__signature-cell) {
  align-self: stretch;
}

.user-form-dialog :deep(.user-form-dialog__signature-cell .el-form-item__content) {
  align-items: stretch;
  height: 100%;
}

.user-form-dialog :deep(.user-form-dialog__signature-cell .el-upload) {
  width: 100%;
}

.user-form-dialog :deep(.user-form-dialog__signature-cell .el-upload-dragger) {
  border: none;
  height: auto;
  padding: 0;
}

.user-form-dialog :deep(.user-form-dialog__remark) {
  margin-bottom: 0;
}

.signature-preview-box {
  border-color: var(--el-border-color);
}
</style>
