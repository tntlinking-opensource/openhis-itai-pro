<script setup lang="ts">
import type { ProviderKeyOption, Setting, SettingDefinition } from '#/api/core/system/setting';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

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
const editingId = ref<null | number>(null);
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

async function handleCreate() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  const v = await formApi.getValues();
  await createSettingApi({
    name: nameText.value,
    description: titleText.value,
    value: v.value || '',
    providerName: v.providerName,
    providerKey: v.providerName === 'G' ? '' : v.providerKey,
  });
  ElMessage.success('保存成功');
  await loadSettings();
  await formApi.setValues({
    providerKey: '',
    value: '',
  });
}

function startEdit(row: Setting) {
  editingId.value = row.id;
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
}

async function handleDelete(row: Setting) {
  await deleteSettingApi({ value: row.id });
  ElMessage.success('删除成功');
  await loadSettings();
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => emit('update:visible', isOpen),
  onOpened: async () => {
    await loadProviders();
    await loadProviderKeyMaps();
    providerName.value = 'G';
    await loadProviderKeys(providerName.value);
    await formApi.resetForm();
    await formApi.setValues({ providerName: 'G', providerKey: '', value: '' });
    await loadSettings();
  },
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
        defaultValue: titleText.value,
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '名称',
        componentProps: { disabled: true },
        defaultValue: nameText.value,
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
          <ElButton type="primary" @click="handleCreate">新增/保存</ElButton>
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
              <div v-if="editingId !== row.id" class="truncate text-xs text-gray-500">
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
                v-if="editingId !== row.id"
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
