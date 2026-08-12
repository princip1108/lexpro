<template>
  <div class="page recommend-page">
    <div class="page-title-row"><h2 class="page-title">典型案例推荐</h2><el-button type="primary" :icon="Search" :disabled="!caseId" :loading="searching" @click="recommend">开始推荐</el-button></div>
    <CaseContextBar v-model="caseId" @change="loadHistory" />
    <SectionCard title="检索事实"><el-input v-model="factText" type="textarea" :rows="6" maxlength="100000" show-word-limit placeholder="输入虚构或已获授权的案件事实，系统调用本地 BGE-M3 混合检索"/><div class="focus-row"><el-input v-model="focusText" placeholder="争议焦点，多个焦点用分号分隔"/><el-input-number v-model="limit" :min="1" :max="20"/></div></SectionCard>
    <div class="recommend-layout">
      <SectionCard title="推荐历史"><el-table :data="history" highlight-current-row @current-change="openRecommendation"><el-table-column prop="recommendId" label="批次" width="80"/><el-table-column prop="modelName" label="模型" min-width="160"/><el-table-column prop="pipelineVersion" label="检索流程" min-width="130"/><el-table-column label="创建时间" width="180"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column></el-table><el-empty v-if="!history.length" description="暂无推荐历史"/></SectionCard>
      <SectionCard title="推荐结果"><el-empty v-if="!detail?.items?.length" description="请选择推荐批次"/><div v-else class="result-list"><article v-for="item in detail.items" :key="item.itemId"><div class="rank">{{ item.rank }}</div><div><h3>{{ item.title }}</h3><p>{{ item.caseCause || '未标注案由' }} · {{ item.court || '法院未记录' }} · {{ item.judgmentDate || '日期未记录' }}</p><div class="reason-list"><el-tag v-for="reason in reasonList(item.reasons)" :key="reason" type="info">{{ reason }}</el-tag></div></div><strong>{{ score(item.score) }}</strong></article></div></SectionCard>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import CaseContextBar from '../components/common/CaseContextBar.vue'
import SectionCard from '../components/common/SectionCard.vue'
import { createRecommendation, getRecommendation, listRecommendations } from '../api/recommendations'
const caseId=ref(null);const factText=ref('');const focusText=ref('');const limit=ref(10);const history=ref([]);const detail=ref(null);const searching=ref(false)
async function loadHistory(){detail.value=null;if(!caseId.value)return;try{history.value=await listRecommendations(caseId.value);if(history.value.length)await openRecommendation(history.value[0])}catch(error){ElMessage.error(error.message)}}
async function openRecommendation(row){if(!row)return;try{detail.value=await getRecommendation(caseId.value,row.recommendId)}catch(error){ElMessage.error(error.message)}}
async function recommend(){if(!factText.value.trim())return ElMessage.warning('请输入案件事实');searching.value=true;try{detail.value=await createRecommendation(caseId.value,{factText:factText.value,disputeFocus:focusText.value.split(/[；;]/).map((i)=>i.trim()).filter(Boolean),filters:null,limit:limit.value});ElMessage.success('推荐完成');await loadHistory()}catch(error){ElMessage.error(error.message)}finally{searching.value=false}}
function reasonList(value){if(Array.isArray(value))return value.map((i)=>typeof i==='string'?i:(i.text||i.label||JSON.stringify(i)));if(value&&typeof value==='object')return Object.values(value).flat().map((i)=>typeof i==='string'?i:JSON.stringify(i));return []}function score(v){return v==null?'-':Number(v).toFixed(3)}function formatTime(v){return v?new Date(v).toLocaleString('zh-CN'):'-'}
</script>
<style scoped>.recommend-page{display:grid;gap:16px}.focus-row{display:grid;grid-template-columns:1fr auto;gap:12px;margin-top:12px}.recommend-layout{display:grid;grid-template-columns:minmax(420px,.8fr) minmax(500px,1.2fr);gap:16px;align-items:start}.result-list{display:grid;gap:12px}.result-list article{display:grid;grid-template-columns:36px minmax(0,1fr) 60px;gap:12px;align-items:start;padding:14px;border:1px solid #e6ebf2;border-radius:6px}.rank{display:grid;width:30px;height:30px;color:white;background:var(--primary);border-radius:4px;place-items:center}.result-list h3{margin:0;font-size:15px}.result-list p{margin:6px 0;color:#64748b;font-size:13px}.reason-list{display:flex;flex-wrap:wrap;gap:6px}@media(max-width:1000px){.recommend-layout{grid-template-columns:1fr}}</style>
