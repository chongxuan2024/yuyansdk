package com.yuyan.imemodule.data.model

data class KnowledgeBase(
    val id: String,
    val name: String,
    val paymentType: PaymentType,
    val templateType: TemplateType,
    val owner: String,
    val createdAt: Long,
    val members: List<Member> = emptyList()
)

enum class PaymentType {
    USER_PAID,    // 使用者付费
    ENTERPRISE_PAID  // 企业付费
}

enum class TemplateType {
    PERSONAL_CHAT,    // 个人聊天
    PRODUCT_SUPPORT,  // 产品答疑
    CUSTOMER_SERVICE, // 售后服务
    CUSTOM           // 自定义
}

data class Member(
    val username: String,
    val role: Role
)

enum class Role {
    ADMIN,     // 管理员
    USER       // 普通用户
}