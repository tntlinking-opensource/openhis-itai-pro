<script lang="ts" setup>
/**
 * 码上放心供应商对照 · 编辑弹窗。
 */
import type { MsfxEntInfoRowDto } from '#/api/msfx/box-enterprise';
import type { MsfxSupplierMapItem } from '#/api/msfx/supplier-map';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { listMsfxEntInfoApi } from '#/api/msfx/box-enterprise';
import { initMsfxSupplierMapApi, saveMsfxSupplierMapApi } from '#/api/msfx/supplier-map';

defineOptions({ name: 'BasicMsfxSupplierMapEditDialog' });

const props = withDefaults(
  defineProps<{
    visible?: boolean;
    formData?: Partial<MsfxSupplierMapItem>;
  }>(),
  {
    visible: false,
    formData: () => ({}),
  },
);

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
});

const saving = ref(false);
const supplierOptions = ref<Array<{ label: string; value: string }>>([]);
const entOptions = ref<MsfxEntInfoRowDto[]>([]);
const entLoading = ref(false);
let entTimer: ReturnType<typeof setTimeout> | undefined;

const form = reactive({
  id: '' as string | number | '',
  supplierId: '' as string,
  msfxEntId: '',
  msfxRefEntId: '',
  msfxEntName: '',
  remark: '',
});

const isEdit = computed(() => form.id !== '' && form.id !== undefined && form.id !== null);

watch(
  () => props.visible,
  async (v) => {
    if (!v) return;
    await loadInit();
    const d = props.formData ?? {};
    form.id = d.id ?? '';
    form.supplierId =
      d.supplierId !== undefined && d.supplierId !== null ? String(d.supplierId) : '';
    form.msfxEntId = String(d.msfxEntId ?? '');
    form.msfxRefEntId = String(d.msfxRefEntId ?? '');
    form.msfxEntName = String(d.msfxEntName ?? '');
    form.remark = String(d.remark ?? '');
    // 编辑态：把当前企业塞进下拉，便于回显
    if (form.msfxEntId) {
      entOptions.value = [
        {
          entId: form.msfxEntId,
          refEntId: form.msfxRefEntId || undefined,
          entName: form.msfxEntName || form.msfxEntId,
        },
      ];
    } else {
      entOptions.value = [];
    }
  },
);

async function loadInit() {
  try {
    const res = await initMsfxSupplierMapApi();
    supplierOptions.value = res.data?.supplierOptions ?? [];
  } catch (error) {
    supplierOptions.value = [];
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: string }).message)
        : '加载供应商失败';
    ElMessage.error(msg);
  }
}

function remoteSearchEnt(keyword: string) {
  if (entTimer) clearTimeout(entTimer);
  const q = String(keyword ?? '').trim();
  if (q.length < 2) {
    return;
  }
  entTimer = setTimeout(() => {
    void doSearchEnt(q);
  }, 300);
}

async function doSearchEnt(entName: string) {
  entLoading.value = true;
  try {
    const res = await listMsfxEntInfoApi({ entName });
    entOptions.value = res.data?.rows ?? [];
  } catch (error) {
    entOptions.value = [];
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: string }).message)
        : '';
    ElMessage.info(msg.includes('未启用') ? msg : '平台企业名搜索暂不可用，可直接手填下方 ent_id');
  } finally {
    entLoading.value = false;
  }
}

function onEntPicked(entId: string) {
  const hit = entOptions.value.find((r) => String(r.entId ?? '') === String(entId));
  if (!hit) return;
  form.msfxEntId = String(hit.entId ?? '');
  form.msfxRefEntId = String(hit.refEntId ?? '');
  form.msfxEntName = String(hit.entName ?? '');
}

async function handleSave() {
  if (!form.supplierId) {
    ElMessage.warning('请选择 HIS 供应商');
    return;
  }
  if (!String(form.msfxEntId).trim()) {
    ElMessage.warning('请选择或填写码上放心 ent_id');
    return;
  }
  saving.value = true;
  try {
    await saveMsfxSupplierMapApi({
      id: isEdit.value ? form.id : undefined,
      supplierId: form.supplierId,
      msfxEntId: String(form.msfxEntId).trim(),
      msfxRefEntId: String(form.msfxRefEntId).trim() || undefined,
      msfxEntName: String(form.msfxEntName).trim() || undefined,
      remark: String(form.remark).trim() || undefined,
    });
    ElMessage.success('已保存');
    dialogVisible.value = false;
    emit('success');
  } catch (error) {
    const msg =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: string }).message)
        : '保存失败';
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑对照' : '新增对照'"
    width="560px"
    destroy-on-close
    append-to-body
  >
    <ElForm label-width="120px">
      <ElFormItem label="HIS 供应商" required>
        <ElSelect
          v-model="form.supplierId"
          filterable
          clearable
          class="w-full"
          placeholder="选择供应商"
          :disabled="isEdit"
        >
          <ElOption
            v-for="opt in supplierOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="平台企业" required>
        <ElSelect
          v-model="form.msfxEntId"
          filterable
          remote
          clearable
          reserve-keyword
          class="w-full"
          placeholder="输入企业名搜索"
          :remote-method="remoteSearchEnt"
          :loading="entLoading"
          @change="onEntPicked"
        >
          <ElOption
            v-for="opt in entOptions"
            :key="`${opt.entId}-${opt.refEntId}`"
            :label="`${opt.entName || '未命名'}（${opt.entId || '-'}）`"
            :value="String(opt.entId ?? '')"
            :disabled="!opt.entId"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="ent_id" required>
        <ElInput v-model="form.msfxEntId" clearable placeholder="可手填" />
      </ElFormItem>
      <ElFormItem label="ref_ent_id">
        <ElInput v-model="form.msfxRefEntId" clearable />
      </ElFormItem>
      <ElFormItem label="平台企业名">
        <ElInput v-model="form.msfxEntName" clearable />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="form.remark" type="textarea" :rows="2" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
    </template>
  </ElDialog>
</template>
