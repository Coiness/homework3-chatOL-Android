#!/usr/bin/env node

/**
 * copy-web.js - 将外部 Web 构建文件复制到 Android assets 目录
 * 
 * 使用方法:
 *   node scripts/copy-web.js <source_dir>
 * 
 * 示例:
 *   node scripts/copy-web.js ../web-app/dist
 *   node scripts/copy-web.js /path/to/web/build
 * 
 * 功能:
 *   1. 清空目标 assets 目录 (保留 .gitkeep)
 *   2. 复制源目录中的所有文件到 assets
 *   3. 验证必要文件存在 (index.html)
 */

const fs = require('fs');
const path = require('path');

// 配置
const ASSETS_DIR = path.resolve(__dirname, '../app/src/main/assets');
const REQUIRED_FILES = ['index.html'];

/**
 * 递归删除目录内容 (保留目录本身)
 */
function cleanDirectory(dir, keepFiles = []) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
        return;
    }
    
    const items = fs.readdirSync(dir);
    for (const item of items) {
        if (keepFiles.includes(item)) continue;
        
        const itemPath = path.join(dir, item);
        const stat = fs.statSync(itemPath);
        
        if (stat.isDirectory()) {
            fs.rmSync(itemPath, { recursive: true, force: true });
        } else {
            fs.unlinkSync(itemPath);
        }
    }
}

/**
 * 递归复制目录
 */
function copyDirectory(src, dest) {
    if (!fs.existsSync(dest)) {
        fs.mkdirSync(dest, { recursive: true });
    }
    
    const items = fs.readdirSync(src);
    let copiedCount = 0;
    
    for (const item of items) {
        const srcPath = path.join(src, item);
        const destPath = path.join(dest, item);
        const stat = fs.statSync(srcPath);
        
        if (stat.isDirectory()) {
            copiedCount += copyDirectory(srcPath, destPath);
        } else {
            fs.copyFileSync(srcPath, destPath);
            copiedCount++;
        }
    }
    
    return copiedCount;
}

/**
 * 验证必要文件存在
 */
function validateFiles(dir, requiredFiles) {
    const missing = [];
    
    for (const file of requiredFiles) {
        const filePath = path.join(dir, file);
        if (!fs.existsSync(filePath)) {
            missing.push(file);
        }
    }
    
    return missing;
}

/**
 * 主函数
 */
function main() {
    const args = process.argv.slice(2);
    
    if (args.length === 0) {
        console.error('错误: 请指定源目录');
        console.error('');
        console.error('使用方法:');
        console.error('  node scripts/copy-web.js <source_dir>');
        console.error('');
        console.error('示例:');
        console.error('  node scripts/copy-web.js ../web-app/dist');
        process.exit(1);
    }
    
    const sourceDir = path.resolve(args[0]);
    
    // 检查源目录是否存在
    if (!fs.existsSync(sourceDir)) {
        console.error(`错误: 源目录不存在: ${sourceDir}`);
        process.exit(1);
    }
    
    if (!fs.statSync(sourceDir).isDirectory()) {
        console.error(`错误: 指定路径不是目录: ${sourceDir}`);
        process.exit(1);
    }
    
    console.log('========================================');
    console.log('  复制 Web 构建文件到 Android Assets');
    console.log('========================================');
    console.log('');
    console.log(`源目录: ${sourceDir}`);
    console.log(`目标目录: ${ASSETS_DIR}`);
    console.log('');
    
    // 清空目标目录
    console.log('1. 清空目标目录...');
    cleanDirectory(ASSETS_DIR, ['.gitkeep']);
    console.log('   完成');
    
    // 复制文件
    console.log('2. 复制文件...');
    const copiedCount = copyDirectory(sourceDir, ASSETS_DIR);
    console.log(`   复制了 ${copiedCount} 个文件`);
    
    // 验证必要文件
    console.log('3. 验证必要文件...');
    const missing = validateFiles(ASSETS_DIR, REQUIRED_FILES);
    
    if (missing.length > 0) {
        console.error(`   警告: 以下必要文件缺失: ${missing.join(', ')}`);
        console.error('   请确保 Web 构建包含 index.html');
    } else {
        console.log('   验证通过');
    }
    
    console.log('');
    console.log('========================================');
    console.log('  完成!');
    console.log('========================================');
}

main();
