package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送消息请求")
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "附件URL")
    private String attachmentUrl;

    @Schema(description = "客户端消息幂等键")
    @Size(max = 64, message = "客户端消息标识过长")
    private String clientMessageId;
}
