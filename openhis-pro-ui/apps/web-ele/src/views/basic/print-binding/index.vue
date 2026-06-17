<script setup lang="ts">
import type { PrintBindingItemDto } from '#/api/report/report-print-binding';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPrintBindingConfigApi } from '#/api/report/report-print-binding';
import { useRequest } from '#/hooks/use-request';
import {
  formatReportLayoutMode,
  formatReportPipeline,
  formatReportSource,
} from '../report-template/report-template.constants';
import { savePrintBindings } from '#/utils/report/print-binding-store';

defineOptions({ name: 'ReportPrintBinding' });

const router = useRouter();
const items = ref<PrintBindingItemDto[]>([]);
const draft = ref<Record<string, string>>({});
const dirty = ref(false);

const { run: runLoad, loading: loadLoading } = useRequest(getPrintBindingConfigApi, {
  manual: true,
  onSuccess: (config) => {
    items.value = config.items ?? [];
    const next: Record<string, string> = {};
    for (const item of items.value) {
      if (item.bindingCode && item.templateCode) {
        next[item.bindingCode] = item.templateCode;
      }
    }
    draft.value = next;
    dirty.value = false;
  },
});

const { run: runSave, loading: saveLoading } = useRequest(
  async () => {
    await savePrintBindings(draft.value);
    await runLoad();
  },
  { manual: true },
);

onMounted(() => {
  void runLoad();
});

function candidateLabel(code: string, name?: string) {
  if (name) {
    return `${name}（${code}）`;
  }
  return code;
}

function onTemplateChange(bindingCode: string, templateCode: string) {
  draft.value = { ...draft.value, [bindingCode]: templateCode };
  dirty.value = true;
}

function openDesigner(templateCode: string) {
  router.push({
    path: '/basic/report-template/designer',
    query: { code: templateCode },
  });
}

async function handleSave() {
  await runSave();
  ElMessage.success('院点打印绑定已保存');
}

async function handleReset() {
  await runLoad();
  ElMessage.info('已恢复为服务器配置');
}
</script>

<template>
  <Page auto-content-height>
    <div v-loading="loadLoading" class="print-binding">
      <header class="print-binding__header">
        <div>
          <h2 class="print-binding__title">院点打印模板配置</h2>
          <p class="print-binding__desc">
            为各业务场景指定已发布的 templateCode。保存后全院工作站生效（替代浏览器 localStorage
            本地配置）。
          </p>
        </div>
        <div class="print-binding__actions">
          <ElButton :disabled="!dirty || saveLoading" @click="handleReset">重置</ElButton>
          <ElButton type="primary" :loading="saveLoading" :disabled="!dirty" @click="handleSave">
            保存
          </ElButton>
        </div>
      </header>

      <ElAlert
        type="info"
        :closable="false"
        show-icon
        class="print-binding__alert"
        title="若下拉中无自定义模板，请先在「打印模板」中复制/设计并发布；同编码院点发布版将覆盖出厂模板。"
      />

      <ElTable :data="items" border stripe class="print-binding__table">
        <ElTableColumn prop="bizModule" label="业务模块" width="110" />
        <ElTableColumn prop="bindingName" label="场景" min-width="140" />
        <ElTableColumn prop="scene" label="说明" min-width="180" show-overflow-tooltip />
        <ElTableColumn label="当前模板" min-width="280">
          <template #default="{ row }">
            <ElSelect
              :model-value="draft[row.bindingCode!] ?? row.templateCode"
              filterable
              style="width: 100%"
              @update:model-value="(v: string) => onTemplateChange(row.bindingCode!, v)"
            >
              <ElOption
                v-for="opt in row.candidates ?? []"
                :key="opt.code"
                :label="candidateLabel(opt.code!, opt.name)"
                :value="opt.code!"
              />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn label="模式 / 管线" width="150">
          <template #default="{ row }">
            <template v-for="opt in row.candidates ?? []" :key="opt.code">
              <span
                v-if="opt.code === (draft[row.bindingCode!] ?? row.templateCode)"
                class="print-binding__meta"
              >
                {{ formatReportLayoutMode(opt.layoutMode) }}
                <br />
                {{ formatReportPipeline(opt.pipeline) }}
              </span>
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn label="来源" width="88" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.tenantOverride" size="small" type="success">院点</ElTag>
            <ElTag v-else size="small" type="info">默认</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="88" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDesigner(draft[row.bindingCode!] ?? row.templateCode ?? '')"
            >
              设计
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </Page>
</template>

<style scoped>
.print-binding {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.print-binding__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.print-binding__title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.print-binding__desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.print-binding__actions {
  display: flex;
  gap: 8px;
}

.print-binding__alert {
  flex-shrink: 0;
}

.print-binding__table {
  flex: 1;
}

.print-binding__meta {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
