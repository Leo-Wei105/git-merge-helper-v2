package com.gitmergehelper.model

/**
 * Git合并助手配置数据模型
 * 
 * @property mainBranch 主分支名称
 * @property targetBranches 目标分支列表
 * @property featureBranchPatterns 功能分支命名模式列表
 * @property branchPrefixes 分支前缀列表
 * @property customGitName 自定义Git用户名（可选，为空时使用全局Git用户名）
 */
data class GitMergeConfig(
    var mainBranch: String = "main",
    var targetBranches: MutableList<TargetBranch> = mutableListOf(),
    var featureBranchPatterns: MutableList<String> = mutableListOf(),
    var branchPrefixes: MutableList<BranchPrefix> = mutableListOf(),
    var customGitName: String = ""
) {
    companion object {
        /**
         * 获取默认配置
         * 
         * @return 包含默认设置的GitMergeConfig实例
         */
        fun getDefault(): GitMergeConfig {
            return GitMergeConfig(
                mainBranch = "main",
                targetBranches = mutableListOf(
                    TargetBranch("main", "主分支"),
                    TargetBranch("develop", "开发分支"),
                    TargetBranch("release", "发布分支")
                ),
                featureBranchPatterns = mutableListOf(
                    "feature/*",
                    "feat/*",
                    "bugfix/*",
                    "hotfix/*",
                    "fix/*"
                ),
                branchPrefixes = mutableListOf(
                    BranchPrefix("feature", "功能分支", true),
                    BranchPrefix("feat", "功能分支(简写)", false),
                    BranchPrefix("bugfix", "修复分支", false),
                    BranchPrefix("hotfix", "热修复分支", false),
                    BranchPrefix("fix", "修复分支(简写)", false)
                ),
                customGitName = ""
            )
        }
    }
    
    /**
     * 验证配置是否有效
     * 
     * @return 配置验证结果
     */
    fun validate(): ConfigValidationResult {
        val errors = mutableListOf<String>()
        
        // 验证主分支
        if (mainBranch.isBlank()) {
            errors.add("主分支名称不能为空")
        }
        
        // 验证目标分支
        if (targetBranches.isEmpty()) {
            errors.add("至少需要配置一个目标分支")
        }
        
        // 验证功能分支模式
        if (featureBranchPatterns.isEmpty()) {
            errors.add("至少需要配置一个功能分支模式")
        }
        
        // 验证分支前缀
        if (branchPrefixes.isEmpty()) {
            errors.add("至少需要配置一个分支前缀")
        }
        
        // 检查是否有默认分支前缀
        if (branchPrefixes.none { it.isDefault }) {
            errors.add("至少需要设置一个默认分支前缀")
        }
        
        // 检查重复的目标分支
        val duplicateBranches = targetBranches.groupBy { it.name }
            .filter { it.value.size > 1 }
            .keys
        if (duplicateBranches.isNotEmpty()) {
            errors.add("存在重复的目标分支: ${duplicateBranches.joinToString(", ")}")
        }
        
        // 检查重复的功能分支模式
        val duplicatePatterns = featureBranchPatterns.groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        if (duplicatePatterns.isNotEmpty()) {
            errors.add("存在重复的功能分支模式: ${duplicatePatterns.joinToString(", ")}")
        }
        
        // 检查重复的分支前缀
        val duplicatePrefixes = branchPrefixes.groupBy { it.prefix }
            .filter { it.value.size > 1 }
            .keys
        if (duplicatePrefixes.isNotEmpty()) {
            errors.add("存在重复的分支前缀: ${duplicatePrefixes.joinToString(", ")}")
        }
        
        return ConfigValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * 检查分支是否匹配功能分支模式
     * 
     * @param branchName 分支名称
     * @return 是否为功能分支
     */
    fun isFeatureBranch(branchName: String): Boolean {
        return featureBranchPatterns.any { pattern ->
            branchName.matches(pattern.replace("*", ".*").toRegex())
        }
    }
    
    /**
     * 获取默认分支前缀
     * 
     * @return 默认分支前缀，如果没有则返回第一个
     */
    fun getDefaultBranchPrefix(): BranchPrefix? {
        return branchPrefixes.find { it.isDefault } ?: branchPrefixes.firstOrNull()
    }
}

/**
 * 目标分支配置
 * 
 * @property name 分支名称
 * @property description 分支描述
 */
data class TargetBranch(
    var name: String,
    var description: String
) {
    override fun toString(): String = "$name ($description)"
}

/**
 * 分支前缀配置
 * 
 * @property prefix 前缀名称
 * @property description 前缀描述
 * @property isDefault 是否为默认前缀
 */
data class BranchPrefix(
    var prefix: String,
    var description: String = "",
    var isDefault: Boolean = false
) {
    override fun toString(): String = "$prefix ($description)"
    
    companion object {
        /**
         * 获取默认分支前缀列表
         * 
         * @return 默认分支前缀列表
         */
        fun getDefaults(): List<BranchPrefix> {
            return listOf(
                BranchPrefix("feature", "功能分支", true),
                BranchPrefix("feat", "功能分支(简写)", false),
                BranchPrefix("bugfix", "修复分支", false),
                BranchPrefix("hotfix", "热修复分支", false),
                BranchPrefix("fix", "修复分支(简写)", false)
            )
        }
    }
}

/**
 * 配置验证结果
 * 
 * @property isValid 是否有效
 * @property errors 错误信息列表
 */
data class ConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String>
) 