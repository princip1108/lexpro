<template>
  <div v-loading="loading">
    <div class="toolbar"><span class="muted">最近 200 条操作日志</span><button class="btn ghost" style="margin-left:auto" type="button" @click="load">刷新</button></div>
    <div class="card"><div class="card-title"><span class="bar"></span>最近操作日志</div><div class="card-pad">
      <div v-for="row in rows" :key="row.operationId" class="log-line"><span class="t">{{ auditAction(row.action) }}</span><span class="d">{{ auditDetail(row.detail) }}</span><span class="muted">{{ row.username || '' }} · {{ auditTarget(row.target) }} · {{ formatDate(row.createdAt) }}</span></div>
      <div v-if="!rows.length && !loading" class="empty">暂无日志</div>
    </div></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listOperationLogs } from '../api/system'
import { auditAction, auditTarget, auditDetail } from '../utils/auditLabels'
const loading = ref(false), rows = ref([])
onMounted(load)
async function load(){loading.value=true;try{const data=await listOperationLogs();rows.value=data.items||[]}catch(error){ElMessage.error(error.message)}finally{loading.value=false}}
function formatDate(value){return value?new Intl.DateTimeFormat('zh-CN',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value)):'-'}
</script>
