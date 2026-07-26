package com.kangban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangban.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
