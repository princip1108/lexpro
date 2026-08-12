<template>
  <div class="context-bar">
    <div>
      <span>当前案件</span>
      <strong>{{ selectedCase?.caseName || '请选择案件' }}</strong>
    </div>
    <el-select v-model="model" filterable placeholder="选择可访问案件" :loading="loading" @change="emit('change')">
      <el-option v-for="item in cases" :key="item.caseId" :value="item.caseId" :label="`${item.caseName}${item.caseNo ? `（${item.caseNo}）` : ''}`" />
    </el-select>
    <el-button :icon="Refresh" circle title="刷新案件" @click="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { listCases } from '../../api/cases'

const model = defineModel({ type: Number, default: null })
const emit = defineEmits(['change', 'loaded'])
const cases = ref([])
const loading = ref(false)
const route = useRoute()
const selectedCase = computed(() => cases.value.find((item) => item.caseId === model.value))

onMounted(load)
async function load() {
  loading.value = true
  try {
    const result = await listCases({ page: 1, size: 100 })
    cases.value = result.items
    const requestedCaseId = Number(route.query.caseId)
    if (!model.value && Number.isInteger(requestedCaseId) && cases.value.some((item) => item.caseId === requestedCaseId)) {
      model.value = requestedCaseId
    }
    if (!model.value && cases.value.length) model.value = cases.value[0].caseId
    emit('loaded', selectedCase.value)
    emit('change')
  } finally { loading.value = false }
}
</script>

<style scoped>
.context-bar { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(280px, 420px) 36px; align-items: center; gap: 12px; padding: 14px 18px; background: #fff; border: 1px solid #e5eaf2; border-radius: 6px; }
.context-bar div { display: grid; gap: 3px; }
.context-bar span { color: #64748b; font-size: 12px; }
.context-bar strong { color: #1f2d3d; font-size: 16px; }
@media (max-width: 760px) { .context-bar { grid-template-columns: 1fr 36px; } .context-bar div { grid-column: 1 / -1; } }
</style>
