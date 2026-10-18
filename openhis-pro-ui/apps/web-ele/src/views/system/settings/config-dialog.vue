<script setup lang="ts">
import type { ProviderKeyOption, Setting, SettingDefinition } from '#/api/core/system/setting';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElButton, ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createSettingApi,
  deleteSettingApi,
  getSettingProviderKeysApi,
  initSettingApi,
  pageSettingsApi,
  updateSettingApi,
} from '#/api/core/system/setting';

interface Props {
  visible?: boolean;
  definition?: null | SettingDefinition;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  definition: null,
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const providers = ref<Array<{ label: string; value: string }>>([]);
const providerKeyOptions = ref<ProviderKeyOption[]>([]);
const providerName = ref<string>('G');

const settings = ref<Setting[]>([]);
const loading = ref(false);
const editingId = ref<null | string>(null);
const editingValue = ref<string>('');

const userKeyMap = ref<Record<string, string>>({});
const roleKeyMap = ref<Record<string, string>>({});
const tenantKeyMap = ref<Record<string, string>>({});

const titleText = computed(() => props.definition?.title || '配置');
const nameText = computed(() => props.definition?.name || '');

async function loadProviders() {
  const init = await initSettingApi();
  providers.value = init.providers || [];
}

async function loadProviderKeys(p: string) {
  providerKeyOptions.value = p && p !== 'G' ? await getSettingProviderKeysApi(p) : [];
}

async function loadProviderKeyMaps() {
  const [users, roles, tenants] = await Promise.all([
    getSettingProviderKeysApi('U').catch(() => [] as ProviderKeyOption[]),
    getSettingProviderKeysApi('R').catch(() => [] as ProviderKeyOption[]),
    getSettingProviderKeysApi('T').catch(() => [] as ProviderKeyOption[]),
  ]);

  const toMap = (list: ProviderKeyOption[]) => {
    const map: Record<string, string> = {};
    for (const x of list) {
      map[x.key] = x.label;
    }
    return map;
  };

  userKeyMap.value = toMap(users);
  roleKeyMap.value = toMap(roles);
  tenantKeyMap.value = toMap(tenants);
}

function formatProviderKey(row: Setting) {
  const key = row.providerKey || '';
  if (!key) return '';
  if (row.providerName === 'U')
    return `${key} - ${userKeyMap.value[key] || ''}`.trim().replace(/-\s*$/, '');
  if (row.providerName === 'R')
    return `${key} - ${roleKeyMap.value[key] || ''}`.trim().replace(/-\s*$/, '');
  if (row.providerName === 'T')
    return `${key} - ${tenantKeyMap.value[key] || ''}`.trim().replace(/-\s*$/, '');
  return key;
}

async function loadSettings() {
  if (!nameText.value) return;
  loading.value = true;
  try {
    const res = await pageSettingsApi({
      pageNo: 1,
      pageSize: 200,
      name: nameText.value,
    });
    settings.value = res.content || [];
  } finally {
    loading.value = false;
  }
}

/**
 * 打开弹窗时回填左侧表单：标题/名称随当前定义刷新；
 * 若已有全局(G)配置则把其值回显到「值」输入框，便于核对与改写后另存。
 */
async function syncFormEcho() {
  const globalSetting = settings.value.find((row) => row.providerName === 'G');
  await formApi.setValues({
    title: titleText.value,
    name: nameText.value,
    providerName: 'G',
    providerKey: '',
    value: globalSetting?.value ?? '',
  });
}

/** 提交左侧表单；可选保存成功后关闭弹窗。 */
async function persistForm(closeAfterSave: boolean): Promise<boolean> {
  const { valid } = await formApi.validate();
  if (!valid) return false;

  modalApi.lock(true);
  try {
    const v = await formApi.getValues();
    const provider = v.providerName as string;
    // 全局(G)已存在时走更新，避免回显后再次「创建」触发「设置已存在」。
    const existingGlobal =
      provider === 'G' ? settings.value.find((row) => row.providerName === 'G') : undefined;
    if (existingGlobal) {
      await updateSettingApi({
        id: existingGlobal.id,
        request: {
          description: titleText.value,
          value: v.value || '',
        },
      });
    } else {
      await createSettingApi({
        name: nameText.value,
        description: titleText.value,
        value: v.value || '',
        providerName: provider,
        providerKey: provider === 'G' ? '' : v.providerKey,
      });
    }
    ElMessage.success('保存成功');
    await loadSettings();
    await syncFormEcho();
    if (closeAfterSave) {
      modalApi.close();
    }
    return true;
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err?.msg || err?.message || '保存失败，请稍后重试');
    return false;
  } finally {
    modalApi.unlock();
  }
}

/** 保存当前配置并保持弹窗打开，便于继续维护 G/U/R/T。 */
async function handleSave() {
  await persistForm(false);
}

/** 确定：有未提交的改动则先保存再关闭，否则直接关闭。 */
async function handleConfirm() {
  const values = await formApi.getValues();
  const currentValue = (values.value || '').trim();
  if (!currentValue) {
    modalApi.close();
    return;
  }
  const provider = values.providerName as string;
  const globalSetting =
    provider === 'G' ? settings.value.find((row) => row.providerName === 'G') : undefined;
  // 回显的 G 值未改动时，点确定只关闭，避免无意义写库。
  if (globalSetting != null && currentValue === (globalSetting.value || '').trim()) {
    modalApi.close();
    return;
  }
  await persistForm(true);
}

function startEdit(row: Setting) {
  editingId.value = String(row.id);
  editingValue.value = row.value || '';
}

function cancelEdit() {
  editingId.value = null;
  editingValue.value = '';
}

async function saveEdit(row: Setting) {
  await updateSettingApi({
    id: row.id,
    request: {
      description: titleText.value,
      value: editingValue.value || '',
    },
  });
  ElMessage.success('更新成功');
  cancelEdit();
  await loadSettings();
  await syncFormEcho();
}

async function handleDelete(row: Setting) {
  await deleteSettingApi(row.id);
  ElMessage.success('删除成功');
  await loadSettings();
  await syncFormEcho();
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => emit('update:visible', isOpen),
  onOpened: async () => {
    await loadProviders();
    await loadProviderKeyMaps();
    providerName.value = 'G';
    await loadProviderKeys(providerName.value);
    await formApi.resetForm();
    await loadSettings();
    // 须在列表加载后回填，才能把已有 G 值写到左侧「值」输入框。
    await syncFormEcho();
  },
  onConfirm: handleConfirm,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '关闭',
  destroyOnClose: false,
  closeOnClickModal: false,
});

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { style: { width: '220px' } } },
  layout: 'horizontal',
  schema: computed(() => {
    const schema: any[] = [
      {
        component: 'Input',
        fieldName: 'title',
        label: '标题',
        componentProps: { disabled: true },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '名称',
        componentProps: { disabled: true },
      },
      {
        component: 'Select',
        fieldName: 'providerName',
        label: '提供者名称',
        required: true,
        componentProps: {
          options: providers.value.map((p) => ({
            label: `${p.value} - ${p.label}`,
            value: p.value,
          })),
          onChange: async (val: string) => {
            providerName.value = val;
            await formApi.setFieldValue('providerKey', '');
            await loadProviderKeys(val);
          },
        },
      },
    ];

    if (providerName.value && providerName.value !== 'G') {
      schema.push({
        component: 'Select',
        fieldName: 'providerKey',
        label: '提供者键',
        required: true,
        componentProps: {
          options: providerKeyOptions.value.map((x) => ({ label: x.label, value: x.key })),
          filterable: true,
        },
      });
    }

    schema.push({
      component: 'Input',
      fieldName: 'value',
      label: '值',
      required: true,
      componentProps: { placeholder: '请输入值' },
    });

    return schema;
  }),
  showDefaultActions: false,
});

watch(
  () => props.visible,
  (v) => (v ? modalApi.open() : modalApi.close()),
  { immediate: true },
);
</script>

<template>
  <Modal class="w-[820px]" :title="`配置：${titleText}`" :close-on-click-modal="false">
    <div class="flex gap-4">
      <div class="flex-1">
        <div class="mb-2 text-xs text-gray-500">取值优先级：U &gt; R &gt; T &gt; G</div>
        <Form />
        <div class="mt-3 flex justify-end">
          <ElButton type="primary" @click="handleSave">保存</ElButton>
        </div>
      </div>
      <div class="w-[420px]">
        <div class="mb-2 text-sm font-medium">已配置项</div>
        <div v-loading="loading" class="space-y-2">
          <div
            v-for="row in settings"
            :key="row.id"
            class="flex items-center justify-between rounded border px-3 py-2"
          >
            <div class="min-w-0">
              <div class="truncate text-sm">
                {{ row.providerName
                }}<span v-if="row.providerKey">:{{ formatProviderKey(row) }}</span>
              </div>
              <div v-if="editingId !== String(row.id)" class="truncate text-xs text-gray-500">
                {{ row.value }}
              </div>
              <div v-else class="mt-1 flex items-center gap-2">
                <input
                  v-model="editingValue"
                  class="h-7 w-full rounded border px-2 text-xs outline-none"
                  placeholder="请输入值"
                />
                <ElButton size="small" type="primary" @click="saveEdit(row)">保存</ElButton>
                <ElButton size="small" @click="cancelEdit">取消</ElButton>
              </div>
            </div>
            <div class="shrink-0">
              <ElButton
                v-if="editingId !== String(row.id)"
                size="small"
                link
                type="primary"
                @click="startEdit(row)"
              >
                更新
              </ElButton>
              <ElButton size="small" link type="danger" @click="handleDelete(row)">删除</ElButton>
            </div>
          </div>
          <div v-if="settings.length === 0" class="text-xs text-gray-500">暂无配置</div>
        </div>
      </div>
    </div>
  </Modal>
</template>
