package com.gitmergehelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.DialogWrapper
import com.gitmergehelper.services.GitMergeHelperSettings
import com.gitmergehelper.services.GitMergeService
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font

/**
 * 显示Git状态和配置信息的操作
 */
class ShowStatusAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        val settings = GitMergeHelperSettings.getInstance()
        val gitService = GitMergeService.getInstance()
        
        // 获取配置信息
        val config = settings.getConfig()
        val configValidation = config.validate()
        
        // 获取Git状态
        val gitStatus = gitService.getGitStatus(project)
        
        // 构建状态信息
        val statusBuilder = StringBuilder()
        
        // Git状态部分
        statusBuilder.append("=== Git状态信息 ===\n")
        if (gitStatus != null) {
            statusBuilder.append("当前分支: ${gitStatus.currentBranch}\n")
            statusBuilder.append("是否为功能分支: ${if (gitStatus.isFeatureBranch) "是" else "否"}\n")
            statusBuilder.append("未提交更改: ${if (gitStatus.hasUncommittedChanges) "有" else "无"}\n")
            statusBuilder.append("远程状态: ${gitStatus.remoteStatus}\n")
        } else {
            statusBuilder.append("无法获取Git状态（可能不在Git仓库中）\n")
        }
        
        statusBuilder.append("\n")
        
        // 配置信息部分
        statusBuilder.append("=== 配置信息 ===\n")
        statusBuilder.append("主分支: ${config.mainBranch}\n")
        statusBuilder.append("自定义Git用户名: ${if (config.customGitName.isNotBlank()) config.customGitName else "(未设置，使用全局配置)"}\n")
        statusBuilder.append("目标分支数量: ${config.targetBranches.size}\n")
        statusBuilder.append("功能分支模式数量: ${config.featureBranchPatterns.size}\n")
        statusBuilder.append("分支前缀数量: ${config.branchPrefixes.size}\n")
        
        statusBuilder.append("\n目标分支列表:\n")
        if (config.targetBranches.isEmpty()) {
            statusBuilder.append("  (无配置)\n")
        } else {
            config.targetBranches.forEach { branch ->
                statusBuilder.append("  • ${branch.name} - ${branch.description}\n")
            }
        }
        
        statusBuilder.append("\n功能分支模式:\n")
        if (config.featureBranchPatterns.isEmpty()) {
            statusBuilder.append("  (无配置)\n")
        } else {
            config.featureBranchPatterns.forEach { pattern ->
                statusBuilder.append("  • $pattern\n")
            }
        }
        
        statusBuilder.append("\n分支前缀配置:\n")
        if (config.branchPrefixes.isEmpty()) {
            statusBuilder.append("  (无配置)\n")
        } else {
            config.branchPrefixes.forEach { prefix ->
                val defaultMark = if (prefix.isDefault) " [默认]" else ""
                statusBuilder.append("  • ${prefix.prefix} - ${prefix.description}$defaultMark\n")
            }
        }
        
        statusBuilder.append("\n")
        
        // 配置验证结果
        statusBuilder.append("=== 配置验证 ===\n")
        if (configValidation.isValid) {
            statusBuilder.append("配置有效 ✓\n")
        } else {
            statusBuilder.append("配置无效 ✗\n")
            statusBuilder.append("错误信息:\n")
            configValidation.errors.forEach { error ->
                statusBuilder.append("  • $error\n")
            }
        }
        
        // 显示自定义状态对话框
        val dialog = StatusDialog(project, statusBuilder.toString())
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
    
    /**
     * 自定义状态显示对话框
     * 提供更好的用户体验，避免内容过长时出现滚动条
     */
    private class StatusDialog(
        project: com.intellij.openapi.project.Project?, 
        private val statusText: String
    ) : DialogWrapper(project) {
        
        init {
            title = "Git合并助手 - 状态信息"
            setOKButtonText("关闭")
            init()
        }
        
        override fun createCenterPanel(): JComponent? {
            val panel = JPanel(BorderLayout())
            
            // 创建文本区域显示状态信息
            val textArea = JTextArea(statusText)
            textArea.isEditable = false
            textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            textArea.background = UIManager.getColor("Panel.background")
            textArea.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            // 使用滚动面板，但设置合适的尺寸避免出现滚动条
            val scrollPane = JScrollPane(textArea)
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.border = BorderFactory.createEmptyBorder()
            
            // 计算合适的对话框尺寸
            val lines = statusText.split('\n').size
            val maxLineLength = statusText.split('\n').maxOfOrNull { it.length } ?: 50
            
            // 根据内容动态调整尺寸
            val width = minOf(800, maxOf(500, maxLineLength * 8 + 100))
            val height = minOf(600, maxOf(300, lines * 20 + 100))
            
            scrollPane.preferredSize = Dimension(width, height)
            
            panel.add(scrollPane, BorderLayout.CENTER)
            
            return panel
        }
        
        override fun createActions(): Array<Action> {
            // 只显示关闭按钮
            return arrayOf(okAction)
        }
    }
} 