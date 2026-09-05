<template>
  <div v-loading="loading">
    <div class="cause-banner">
      <b>当前生效模型:</b> {{ config.activeMode || '内置规则引擎(离线)' }}
      <span class="mode">配置用于法律要素、摘要、案卡和报告生成；实体识别单独连接 LexPro 服务</span>
    </div>
    <div class="card mt16">
      <div class="card-title"><span class="bar"></span>大模型 API 配置列表
        <button class="btn primary sm" style="margin-left:auto" type="button" v-if="canManage&&!deploymentManaged" @click="openEditor()">+ 新增 API</button>
      </div>
      <div class="card-pad">
        <div class="table-wrap"><table class="table"><thead>
          <tr><th>生效名称</th><th>模型名称</th><th>服务地址</th><th>API Key</th><th>思考</th><th>备注</th><th>状态</th><th>操作</th></tr>
        </thead><tbody>
          <tr v-for="row in config.items || []" :key="row.id" :class="{ 'active-row': row.active }">
            <td><b>{{ row.displayName || row.modelName || '-' }}</b><span v-if="row.active" class="badge b-green" style="margin-left:6px">生效</span></td>
            <td class="mono" style="font-size:12px">{{ row.modelName || '-' }}</td>
            <td class="mono model-url" :title="row.baseUrl || ''">{{ row.baseUrl || '-' }}</td>
            <td class="mono" style="font-size:12px">{{ row.apiKeyMasked || '-' }}</td>
            <td><span class="badge" :class="row.enableThinking ? 'b-green' : 'b-gray'">{{ row.enableThinking ? '开' : '关' }}</span></td>
            <td>{{ row.remark || '-' }}</td>
            <td><span class="badge" :class="row.enabled ? 'b-green' : 'b-gray'">{{ row.enabled ? '启用' : '停用' }}</span></td>
            <td><span v-if="!/^\d+$/.test(row.id)" class="muted-sm">部署配置（只读）</span><template v-else-if="canManage"><button class="btn ghost sm" type="button" @click="openEditor(row)">编辑</button> <button v-if="!row.active&&row.enabled" class="btn success sm" :disabled="saving" @click="activate(row)">设为生效</button> <button class="btn ghost danger sm" :disabled="saving" @click="remove(row)">删除</button></template></td>
          </tr>
          <tr v-if="!(config.items || []).length"><td colspan="8" class="empty">暂无 API 配置。点击右上角“新增 API”接入大模型。</td></tr>
        </tbody></table></div>
      </div>
    </div>
    <div class="card mt16">
      <div class="card-title"><span class="bar"></span>电子卷宗解析引擎</div>
      <div class="card-pad">
        <div class="mt8 mb8"><span class="badge" :class="config.mineru?.enabled ? 'b-green' : 'b-gray'">MinerU {{ config.mineru?.enabled ? '已启用' : '未启用' }}</span> <span class="muted-sm">{{ config.mineru?.baseUrl || '未配置 MinerU 服务地址' }}</span></div>
        <div class="muted-sm mt8">TXT 本地解析；DOCX 提取文字、表格并由 MinerU 识别内嵌图片；PDF、图片调用 MinerU。旧版 DOC 请先另存为 DOCX/PDF。启用状态不代表服务器当前可达。</div>
      </div>
    </div>
    <div v-if="editor" class="overlay" @click.self="closeEditor"><div class="modal"><div class="modal-header"><h3>{{ draft.id?'编辑 API 配置':'新增 API 配置' }}</h3><button class="close-x" @click="closeEditor">×</button></div>
      <form @submit.prevent="save"><div class="modal-body">
        <div class="field"><label>生效名称(系统右上角展示)*</label><input v-model="draft.displayName" required maxlength="100" placeholder="如 LexPro"></div>
        <div class="field"><label>模型名称 *</label><input v-model="draft.modelName" required maxlength="100" placeholder="如 LexPro_8B"></div>
        <div class="field"><label>服务地址(base_url) *</label><input v-model="draft.baseUrl" required maxlength="2048" placeholder="http://127.0.0.1:8001/v1"></div>
        <div class="field"><label>API Key</label><input v-model="draft.apiKey" type="password" autocomplete="new-password" maxlength="4096" :placeholder="draft.id?'留空保留已保存的密钥':'无鉴权服务可留空'"></div>
        <div class="field"><label><input v-model="draft.enableThinking" type="checkbox"> 开启思考模式(模型先推理再回答,更深入但更慢;结构化抽取任务通常建议关闭以提速)</label></div>
        <div class="field"><label>备注</label><input v-model="draft.remark" maxlength="500" placeholder="可选,便于区分多个 API"></div>
        <div class="field"><label><input v-model="draft.enabled" type="checkbox"> 启用该 API(停用则不会被调用)</label></div>
        <div class="field"><label><input v-model="draft.setActive" type="checkbox" :disabled="!draft.enabled"> 保存后设为生效模型</label></div>
      </div><div class="modal-footer"><button type="button" class="btn ghost" :disabled="saving" @click="closeEditor">取消</button><button class="btn primary" :disabled="saving">{{ saving?'保存中…':'保存' }}</button></div></form>
    </div></div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '../auth/session'
import { getModelConfiguration, createModelConfiguration, updateModelConfiguration, activateModelConfiguration, deleteModelConfiguration } from '../api/system'

const loading=ref(false),saving=ref(false),editor=ref(false),canManage=hasPermission('USER_MANAGE')
const config=ref({activeMode:'未配置生效模型',parseEngine:'auto',items:[],mineru:{enabled:false}})
const deploymentManaged=computed(()=>(config.value.items||[]).some(row=>!/^\d+$/.test(row.id)))
const draft=reactive({})
async function load(){loading.value=true;try{config.value=await getModelConfiguration()}catch(error){ElMessage.error(error.message)}finally{loading.value=false}}
onMounted(load)
function openEditor(row){Object.assign(draft,{id:row?.id||null,displayName:row?.displayName||'',modelName:row?.modelName||'',baseUrl:row?.baseUrl||'',apiKey:'',enableThinking:row?.enableThinking||false,remark:row?.remark||'',enabled:row?.enabled??true,setActive:row?.active??true});editor.value=true}
function closeEditor(){if(saving.value)return;draft.apiKey='';editor.value=false}
async function save(){
  saving.value=true
  try{const payload={...draft,setActive:draft.enabled&&draft.setActive};delete payload.id
    if(draft.id&&!/^\d+$/.test(draft.id))throw new Error('这是部署环境配置；请新增 API 保存到模型配置库后再切换')
    await(draft.id?updateModelConfiguration(draft.id,payload):createModelConfiguration(payload))
    draft.apiKey='';editor.value=false;await load();ElMessage.success('配置已保存')
  }catch(error){ElMessage.error(error.message)}finally{saving.value=false}
}
async function activate(row){saving.value=true;try{await activateModelConfiguration(row.id);await load();ElMessage.success('已切换生效模型')}catch(error){ElMessage.error(error.message)}finally{saving.value=false}}
async function remove(row){
  try{await ElMessageBox.confirm('确认删除该 API 配置？删除当前生效项后将选择剩余已启用项；没有可用项时停止生成。','删除 API 配置',{confirmButtonText:'删除',cancelButtonText:'取消'})}catch{return}
  saving.value=true;try{await deleteModelConfiguration(row.id);await load();ElMessage.success('配置已删除')}catch(error){ElMessage.error(error.message)}finally{saving.value=false}
}
</script>

<style scoped>.model-url{font-size:12px;max-width:230px;overflow:hidden;text-overflow:ellipsis}.active-row td{background:#f7fbf8}</style>
