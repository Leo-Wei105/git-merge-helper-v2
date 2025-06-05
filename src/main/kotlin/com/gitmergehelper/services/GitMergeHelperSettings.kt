package com.gitmergehelper.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.gitmergehelper.model.GitMergeConfig
import com.gitmergehelper.model.BranchPrefix
import com.intellij.openapi.project.Project

/**
 * Git合并助手配置管理服务
 * 使用IntelliJ平台的PersistentStateComponent来持久化配置
 */
@Service(Service.Level.APP)
@State(
    name = "GitMergeHelperSettings",
    storages = [Storage("gitMergeHelper.xml")]
)
class GitMergeHelperSettings : PersistentStateComponent<GitMergeHelperSettings.State> {
    
    /**
     * 配置状态类
     */
    class State {
        var mainBranch: String = "main"
        var targetBranches: MutableList<TargetBranchState> = mutableListOf()
        var featureBranchPatterns: MutableList<String> = mutableListOf()
        var branchPrefixes: MutableList<BranchPrefixState> = mutableListOf()
        var customGitName: String = ""
        
        /**
         * 目标分支状态
         */
        class TargetBranchState {
            var name: String = ""
            var description: String = ""
            
            constructor()
            constructor(name: String, description: String) {
                this.name = name
                this.description = description
            }
        }
        
        /**
         * 分支前缀状态
         */
        class BranchPrefixState {
            var prefix: String = ""
            var description: String = ""
            var isDefault: Boolean = false
            
            constructor()
            constructor(prefix: String, description: String, isDefault: Boolean) {
                this.prefix = prefix
                this.description = description
                this.isDefault = isDefault
            }
        }
    }
    
    private var state = State()
    
    companion object {
        /**
         * 获取服务实例
         * 
         * @return GitMergeHelperSettings实例
         */
        fun getInstance(): GitMergeHelperSettings {
            return ApplicationManager.getApplication().getService(GitMergeHelperSettings::class.java)
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
        
        // 如果配置为空或未完整初始化，加载默认配置
        if (shouldLoadDefaultConfig(state)) {
            loadDefaultConfig()
        }
    }
    
    /**
     * 判断是否应该加载默认配置
     * 
     * @param state 当前状态
     * @return 是否需要加载默认配置
     */
    private fun shouldLoadDefaultConfig(state: State): Boolean {
        return state.targetBranches.isEmpty() && 
               state.featureBranchPatterns.isEmpty() && 
               state.branchPrefixes.isEmpty()
    }
    
    /**
     * 获取当前配置
     * 
     * @return GitMergeConfig实例
     */
    fun getConfig(): GitMergeConfig {
        val config = GitMergeConfig()
        config.mainBranch = state.mainBranch
        config.customGitName = state.customGitName
        
        // 转换目标分支
        config.targetBranches.clear()
        config.targetBranches.addAll(
            state.targetBranches.map { 
                com.gitmergehelper.model.TargetBranch(it.name, it.description)
            }
        )
        
        // 转换功能分支模式
        config.featureBranchPatterns.clear()
        config.featureBranchPatterns.addAll(state.featureBranchPatterns)
        
        // 转换分支前缀
        config.branchPrefixes.clear()
        config.branchPrefixes.addAll(
            state.branchPrefixes.map {
                BranchPrefix(it.prefix, it.description, it.isDefault)
            }
        )
        
        return config
    }
    
    /**
     * 保存配置
     * 
     * @param config 要保存的配置
     */
    fun saveConfig(config: GitMergeConfig) {
        state.mainBranch = config.mainBranch
        state.customGitName = config.customGitName
        
        // 转换目标分支
        state.targetBranches.clear()
        state.targetBranches.addAll(
            config.targetBranches.map { 
                State.TargetBranchState(it.name, it.description)
            }
        )
        
        // 转换功能分支模式
        state.featureBranchPatterns.clear()
        state.featureBranchPatterns.addAll(config.featureBranchPatterns)
        
        // 转换分支前缀
        state.branchPrefixes.clear()
        state.branchPrefixes.addAll(
            config.branchPrefixes.map {
                State.BranchPrefixState(it.prefix, it.description, it.isDefault)
            }
        )
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        loadDefaultConfig()
    }
    
    /**
     * 加载默认配置
     */
    private fun loadDefaultConfig() {
        val defaultConfig = GitMergeConfig.getDefault()
        saveConfig(defaultConfig)
    }
    
    /**
     * 验证当前配置
     * 
     * @return 验证结果
     */
    fun validateConfig(): com.gitmergehelper.model.ConfigValidationResult {
        return getConfig().validate()
    }
    
    /**
     * 重置全局配置为默认
     */
    fun resetGlobalConfigToDefault() {
        GitMergeHelperSettings.getInstance().resetToDefault()
    }
    
    /**
     * 重置项目级配置为全局默认
     */
    fun resetProjectConfigToDefault(project: Project) {
        ProjectConfigService.getInstance(project).initFromGlobalConfig()
    }
} 