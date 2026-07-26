package com.kangban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangban.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
