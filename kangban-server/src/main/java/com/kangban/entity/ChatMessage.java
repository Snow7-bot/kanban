package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    private String attachmentUrl;

    private Long replyToMessageId;

    private String clientMessageId;

    /** Agent 本轮检索引用的结构化 JSON；用户消息为空。 */
    private String citationsJson;

    /** Agent 本轮工具执行轨迹的安全 JSON；不包含患者正文或工具参数。 */
    private String agentToolTracesJson;

    private LocalDateTime createdAt;
}
