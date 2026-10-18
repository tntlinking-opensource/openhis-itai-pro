<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';
import type { ReleasePolicyDto } from '#/api/booking/booking-policy';

import { onMounted, reactive, ref } from 'vue';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  deleteReleasePolicyApi,
  loadReleasePolicyApi,
  pageReleasePoliciesApi,
  saveReleasePolicyApi,
} from '#/api/booking/booking-policy';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTooltip,
} from 'element-plus';
import { QuestionFilled } from '@element-plus/icons-vue';

const emit = defineEmits<{
  changed: [];
}>();

const list = ref<ReleasePolicyDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Partial<ReleasePolicyDto>>({});
/** 渠道配额编辑区（入参仍为 JSON 字符串，出参为结构化 channelQuotas） */
const channelQuotaJsonText = ref('');

function quotasToJsonText(quotas?: Record<string, number>): string {
  if (!quotas || Object.keys(quotas).length === 0) {
    return '';
  }
  return JSON.stringify(quotas, null, 2);
}

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '策略编码/名称' },
];

const columns: TableColumn[] = [
  { prop: 'policyName', label: '策略名称', minWidth: 160 },
  { prop: 'advanceDays', label: '提前天数', width: 90 },
  { prop: 'slotMinutes', label: '时段(分)', width: 90 },
  { prop: 'capacityPerSlot', label: '单段容量', width: 90 },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const { run: runPage, loading } = useRequest(pageReleasePoliciesApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

async function loadList() {
  await runPage({
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

function notifyChanged() {
  emit('changed');
}

function handleSearch() {
  query.pageNo = 1;
  void loadList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void loadList();
}

function openCreate() {
  editForm.value = {
    advanceDays: 7,
    slotMinutes: 30,
    capacityPerSlot: 10,
  };
  channelQuotaJsonText.value = '';
  editVisible.value = true;
}

async function openEdit(row: ReleasePolicyDto) {
  if (!row.id) return;
  const detail = await loadReleasePolicyApi({ id: row.id });
  editForm.value = { ...detail };
  channelQuotaJsonText.value = quotasToJsonText(detail.channelQuotas);
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!form.policyName?.trim()) {
    ElMessage.warning('请填写策略名称');
    return;
  }
  await saveReleasePolicyApi({
    id: form.id,
    policyName: form.policyName.trim(),
    advanceDays: Number(form.advanceDays ?? 7),
    slotMinutes: Number(form.slotMinutes ?? 30),
    capacityPerSlot: Number(form.capacityPerSlot ?? 10),
    channelQuotaJson: channelQuotaJsonText.value.trim() || undefined,
  });
  ElMessage.success('放号策略已保存');
  editVisible.value = false;
  await loadList();
  notifyChanged();
}

async function handleDelete(row: ReleasePolicyDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除策略「${row.policyName}」？`, '删除放号策略');
    await deleteReleasePolicyApi({ id: row.id });
    ElMessage.success('已删除');
    await loadList();
    notifyChanged();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    const message = error instanceof Error ? error.message : '删除失败';
    if (message) ElMessage.error(message);
  }
}

onMounted(() => {
  void loadList();
});
</script>

<template>
  <div class="release-policy-panel">
    <p class="release-policy-panel__hint">
      未配置「放号时段」时，自动放号按此处策略的时段粒度与单段容量切分号源；已配置放号时段时以放号时段为准。
    </p>
    <TableLayout
      v-model:query-params="query"
      :form-items="formItems"
      :table-columns="columns"
      :table-data="list"
      :total="total"
      :loading="loading"
      @search="handleSearch"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建策略</ElButton>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="editForm.id ? '编辑放号策略' : '新建放号策略'"
      width="520px"
    >
      <ElForm label-width="100px">
        <ElFormItem label="策略名称" required>
          <ElInput v-model="editForm.policyName" placeholder="门诊默认放号" />
        </ElFormItem>
        <ElFormItem label="提前天数">
          <ElInputNumber v-model="editForm.advanceDays" :min="0" :max="90" />
        </ElFormItem>
        <ElFormItem>
          <template #label>
            <span class="release-policy-panel__label-with-tip">
              时段粒度
              <ElTooltip placement="top" :show-after="200">
                <template #content>
                  自动放号时，将出诊时段按多少<strong>分钟</strong>切分为一段号源。<br />
                  例如填 30：08:00–12:00 会切成 08:00–08:30、08:30–09:00 … 共 8 段。
                </template>
                <ElIcon class="release-policy-panel__tip-icon"><QuestionFilled /></ElIcon>
              </ElTooltip>
            </span>
          </template>
          <ElInputNumber v-model="editForm.slotMinutes" :min="5" :max="240" :step="5" />
          <span class="release-policy-panel__field-unit">分钟/段</span>
        </ElFormItem>
        <ElFormItem>
          <template #label>
            <span class="release-policy-panel__label-with-tip">
              单段容量
              <ElTooltip placement="top" :show-after="200">
                <template #content>
                  每一段号源最多可预约的<strong>人数</strong>（容量上限）。<br />
                  例如填 10：每个时段最多挂 10 个号；与「时段粒度」配合决定放号总量。
                </template>
                <ElIcon class="release-policy-panel__tip-icon"><QuestionFilled /></ElIcon>
              </ElTooltip>
            </span>
          </template>
          <ElInputNumber v-model="editForm.capacityPerSlot" :min="1" :max="999" />
          <span class="release-policy-panel__field-unit">人/段</span>
        </ElFormItem>
        <ElFormItem label="渠道配额">
          <ElInput
            v-model="channelQuotaJsonText"
            type="textarea"
            :rows="3"
            placeholder='JSON，如 {"WINDOW":60,"MINIAPP":40}'
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.release-policy-panel__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.release-policy-panel__label-with-tip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.release-policy-panel__tip-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: help;
}

.release-policy-panel__field-unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
