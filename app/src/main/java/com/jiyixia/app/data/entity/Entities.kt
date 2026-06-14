package com.jiyixia.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,           // Material Icon 名称
    val type: Int,              // 0=支出, 1=收入
    val isPreset: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["date"])]
)
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,              // 0=支出, 1=收入
    val amount: Long,           // 金额，单位：分（38.10元 → 3810L）
    val categoryId: Long,
    val note: String = "",
    val date: Long,             // Unix 时间戳(毫秒)
    val createdAt: Long = System.currentTimeMillis(),
    val isPendingConfirm: Boolean = false,  // 待确认标记
    val confidence: Int = 100,  // 置信度 0-100
    val isReimbursable: Boolean = false,    // 是否可报销（仅支出）
    val isReimbursed: Boolean = false,      // 是否已报销
    val reimbursementTarget: String = "",   // 报销对象（如"XX公司""XX人"）
    val reimbursementSourceId: Long = 0     // 关联的原支出记录ID（仅报销到账收入记录使用）
)
