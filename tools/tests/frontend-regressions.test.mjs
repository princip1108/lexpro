import test from 'node:test'
import assert from 'node:assert/strict'
import {cardFieldPending,cardFieldValue} from '../../src/utils/caseCard.js'
import {ref,toRaw} from 'vue'

test('案卡待确认状态和编辑值保持真实类型',()=>{
  assert.equal(cardFieldPending({confirmStatus:'UNCONFIRMED'}),true)
  assert.equal(cardFieldPending({confirmStatus:'CONFIRMED'}),false)
  assert.equal(cardFieldPending({confirmStatus:'REJECTED'}),false)
  assert.equal(cardFieldValue({value:12000,_value:'12345.67',fieldName:'金额'}),12345.67)
  assert.equal(cardFieldValue({value:false,_value:'是',fieldName:'自首'}),true)
  assert.throws(()=>cardFieldValue({value:1,_value:'非法数字',fieldName:'金额'}))
})

test('实体和要素草稿可克隆 Vue 响应式结果且不改原始数据',()=>{
  const source=ref({elements:[{name:'涉案金额',value:12345.67,evidence:[{quote:'😀𠮷中文'}]}]})
  const draft=structuredClone(toRaw(source.value))
  draft.elements[0].value=9
  assert.equal(source.value.elements[0].value,12345.67)
  assert.equal(draft.elements[0].evidence[0].quote,'😀𠮷中文')
})
