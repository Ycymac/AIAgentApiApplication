package com.ycy.aiapplication.user.service.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账户数据库持久层接口
 */
@Mapper
public interface UserAccountDOMapper extends BaseMapper<UserAccountDO> {

}
