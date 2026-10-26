const fs = require('fs');
const path = require('path');

function walk(dir) {
  fs.readdirSync(dir).forEach(f => {
    const p = path.join(dir, f);
    const stat = fs.statSync(p);
    if (stat.isDirectory()) walk(p);
    else if (f.endsWith('.vue')) check(p);
  });
}

function check(file) {
  const content = fs.readFileSync(file, 'utf-8');
  
  // Check for script tag missing closing quote
  if (content.includes('<script setup lang="ts>')) {
    console.log('ERROR: script缺少闭合引号:', file);
  }
  if (content.includes("<script setup lang='ts>")) {
    console.log('ERROR: script缺少闭合引号:', file);
  }
  
  // Check for ElMessageBox.confirm with double quotes
  if (content.includes('ElMessageBox.confirm(`') && content.includes('"${')) {
    console.log('ERROR: ElMessageBox双引号嵌套:', file);
  }
  
  // Check for unclosed el-form-item
  const openFormItem = (content.match(/<el-form-item[^>]*>/g) || []).length;
  const closeFormItem = (content.match(/<\/el-form-item>/g) || []).length;
  if (openFormItem !== closeFormItem) {
    console.log('WARN: el-form-item不匹配:', file, '开:', openFormItem, '闭:', closeFormItem);
  }
  
  // Check for unclosed el-table-column
  const openCol = (content.match(/<el-table-column[^>]*>(?![^<]*<\/el-table-column>)/g) || []).length;
  const closeCol = (content.match(/<\/el-table-column>/g) || []).length;
  const selfCol = (content.match(/<el-table-column[^>]*\/>/g) || []).length;
  if (openCol !== closeCol + selfCol) {
    console.log('WARN: el-table-column不匹配:', file, '开:', openCol, '闭:', closeCol + selfCol);
  }
}

walk('./src/views/admin');
walk('./src/views/front');