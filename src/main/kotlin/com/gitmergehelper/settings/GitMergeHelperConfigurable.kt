package com.gitmergehelper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.gitmergehelper.model.GitMergeConfig
import com.gitmergehelper.model.TargetBranch
import com.gitmergehelper.services.GitMergeHelperSettings
import javax.swing.*
import javax.swing.table.AbstractTableModel
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Dimension

/**
 * Git合并助手配置界面
 * 实现IntelliJ平台的Configurable接口，提供设置页面
 */
class GitMergeHelperConfigurable : Configurable {
    
    private var mainPanel: JPanel? = null
    private var mainBranchField: JTextField? = null
    private var targetBranchesTable: JTable? = null
    private var targetBranchesModel: TargetBranchTableModel? = null
    private var featurePatternsTable: JTable? = null
    private var featurePatternsModel: FeaturePatternsTableModel? = null
    
    private var config: GitMergeConfig = GitMergeConfig()
    
    override fun getDisplayName(): String = "Git合并助手"
    
    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            mainPanel = createMainPanel()
        }
        return mainPanel
    }
    
    override fun isModified(): Boolean {
        syncUIToConfig()
        val currentConfig = GitMergeHelperSettings.getInstance().getConfig()
        return config != currentConfig
    }
    
    override fun apply() {
        syncUIToConfig()
        GitMergeHelperSettings.getInstance().saveConfig(config)
        Messages.showInfoMessage("配置已保存", "Git合并助手")
    }
    
    override fun reset() {
        config = GitMergeHelperSettings.getInstance().getConfig()
        updateUI()
    }
    
    /**
     * 将UI输入同步到配置对象
     */
    private fun syncUIToConfig() {
        mainBranchField?.let { field ->
            config.mainBranch = field.text.trim()
        }
    }
    
    /**
     * 创建主面板
     */
    private fun createMainPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val contentPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // 主分支配置
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(10, 10, 10, 10)
        contentPanel.add(createMainBranchPanel(), gbc)
        
        // 目标分支配置
        gbc.gridy = 1
        gbc.weighty = 0.4
        gbc.fill = GridBagConstraints.BOTH
        contentPanel.add(createTargetBranchesPanel(), gbc)
        
        // 功能分支模式配置
        gbc.gridy = 2
        gbc.weighty = 0.4
        contentPanel.add(createFeaturePatternsPanel(), gbc)
        
        // 操作按钮
        gbc.gridy = 3
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        contentPanel.add(createActionPanel(), gbc)
        
        panel.add(contentPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建主分支配置面板
     */
    private fun createMainBranchPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("主分支配置")
        
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        
        // 主分支标签
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("主分支名称:"), gbc)
        
        // 主分支输入框
        mainBranchField = JTextField(20)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(mainBranchField!!, gbc)
        
        return panel
    }
    
    /**
     * 创建目标分支配置面板
     */
    private fun createTargetBranchesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("目标分支配置")
        
        // 创建表格模型和表格
        targetBranchesModel = TargetBranchTableModel()
        targetBranchesTable = JTable(targetBranchesModel!!)
        targetBranchesTable!!.preferredScrollableViewportSize = Dimension(400, 150)
        
        val scrollPane = JScrollPane(targetBranchesTable!!)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建按钮面板
        val buttonPanel = JPanel()
        val addButton = JButton("添加")
        val removeButton = JButton("删除")
        
        addButton.addActionListener { addTargetBranch() }
        removeButton.addActionListener { removeTargetBranch() }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    /**
     * 创建功能分支模式配置面板
     */
    private fun createFeaturePatternsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("功能分支模式配置")
        
        // 创建表格模型和表格
        featurePatternsModel = FeaturePatternsTableModel()
        featurePatternsTable = JTable(featurePatternsModel!!)
        featurePatternsTable!!.preferredScrollableViewportSize = Dimension(400, 150)
        
        val scrollPane = JScrollPane(featurePatternsTable!!)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建按钮面板
        val buttonPanel = JPanel()
        val addButton = JButton("添加")
        val removeButton = JButton("删除")
        
        addButton.addActionListener { addFeaturePattern() }
        removeButton.addActionListener { removeFeaturePattern() }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    /**
     * 创建操作按钮面板
     */
    private fun createActionPanel(): JPanel {
        val panel = JPanel()
        
        val resetButton = JButton("重置为默认")
        resetButton.addActionListener { resetToDefault() }
        
        panel.add(resetButton)
        return panel
    }
    
    /**
     * 更新UI显示
     */
    private fun updateUI() {
        mainBranchField?.text = config.mainBranch
        targetBranchesModel?.fireTableDataChanged()
        featurePatternsModel?.fireTableDataChanged()
    }
    
    /**
     * 添加目标分支
     */
    private fun addTargetBranch() {
        val name = Messages.showInputDialog("请输入分支名称:", "添加目标分支", Messages.getQuestionIcon())
        if (!name.isNullOrBlank()) {
            val description = Messages.showInputDialog("请输入分支描述:", "添加目标分支", Messages.getQuestionIcon())
            if (!description.isNullOrBlank()) {
                config.targetBranches.add(TargetBranch(name.trim(), description.trim()))
                targetBranchesModel?.fireTableDataChanged()
            }
        }
    }
    
    /**
     * 删除目标分支
     */
    private fun removeTargetBranch() {
        val selectedRow = targetBranchesTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            config.targetBranches.removeAt(selectedRow)
            targetBranchesModel?.fireTableDataChanged()
        }
    }
    
    /**
     * 添加功能分支模式
     */
    private fun addFeaturePattern() {
        val pattern = Messages.showInputDialog("请输入分支模式 (如: feature/*):", "添加功能分支模式", Messages.getQuestionIcon())
        if (!pattern.isNullOrBlank()) {
            config.featureBranchPatterns.add(pattern.trim())
            featurePatternsModel?.fireTableDataChanged()
        }
    }
    
    /**
     * 删除功能分支模式
     */
    private fun removeFeaturePattern() {
        val selectedRow = featurePatternsTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            config.featureBranchPatterns.removeAt(selectedRow)
            featurePatternsModel?.fireTableDataChanged()
        }
    }
    
    /**
     * 重置为默认配置
     */
    private fun resetToDefault() {
        val choice = Messages.showYesNoDialog(
            "确定要重置为默认配置吗？当前配置将被覆盖。",
            "重置配置",
            Messages.getQuestionIcon()
        )
        
        if (choice == Messages.YES) {
            config = GitMergeConfig.getDefault()
            updateUI()
        }
    }
    
    /**
     * 目标分支表格模型
     */
    private inner class TargetBranchTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("分支名称", "描述")
        
        override fun getRowCount(): Int = config.targetBranches.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val branch = config.targetBranches[rowIndex]
            return when (columnIndex) {
                0 -> branch.name
                1 -> branch.description
                else -> ""
            }
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val branch = config.targetBranches[rowIndex]
            when (columnIndex) {
                0 -> branch.name = aValue.toString()
                1 -> branch.description = aValue.toString()
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
    
    /**
     * 功能分支模式表格模型
     */
    private inner class FeaturePatternsTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("分支模式")
        
        override fun getRowCount(): Int = config.featureBranchPatterns.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            return config.featureBranchPatterns[rowIndex]
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            config.featureBranchPatterns[rowIndex] = aValue.toString()
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
} 