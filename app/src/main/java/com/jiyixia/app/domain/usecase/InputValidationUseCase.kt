package com.jiyixia.app.domain.usecase

/**
 * 输入验证UseCase
 * 负责验证记账输入数据的合法性
 */
object InputValidationUseCase {

    /**
     * 验证金额
     * @param amount 金额（单位：分）
     * @return 验证结果
     */
    fun validateAmount(amount: Long): ValidationResult {
        return when {
            amount < 0 -> ValidationResult.Error("金额不能为负数")
            amount == 0L -> ValidationResult.Error("金额不能为0")
            amount > 100_000_00L -> ValidationResult.Error("金额不能超过100万")
            else -> ValidationResult.Success
        }
    }

    /**
     * 验证备注
     * @param note 备注文本
     * @return 验证结果
     */
    fun validateNote(note: String): ValidationResult {
        return when {
            note.length > 200 -> ValidationResult.Error("备注不能超过200个字符")
            else -> ValidationResult.Success
        }
    }

    /**
     * 验证分类ID
     * @param categoryId 分类ID
     * @return 验证结果
     */
    fun validateCategoryId(categoryId: Long): ValidationResult {
        return when {
            categoryId <= 0 -> ValidationResult.Error("请选择分类")
            else -> ValidationResult.Success
        }
    }

    /**
     * 验证完整的记录数据
     * @param amount 金额
     * @param categoryId 分类ID
     * @param note 备注
     * @return 验证结果列表
     */
    fun validateRecord(amount: Long, categoryId: Long, note: String): List<ValidationResult> {
        return listOf(
            validateAmount(amount),
            validateCategoryId(categoryId),
            validateNote(note)
        )
    }

    /**
     * 检查记录是否有效
     * @param amount 金额
     * @param categoryId 分类ID
     * @param note 备注
     * @return 是否有效
     */
    fun isRecordValid(amount: Long, categoryId: Long, note: String): Boolean {
        return validateRecord(amount, categoryId, note).all { it is ValidationResult.Success }
    }
}

/**
 * 验证结果密封类
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
