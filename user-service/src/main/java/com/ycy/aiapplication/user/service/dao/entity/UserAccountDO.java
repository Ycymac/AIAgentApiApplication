package com.ycy.aiapplication.user.service.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户账户信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("user_account")
public class UserAccountDO {
    /**
     * 用户id（主键）
     */
    @TableId
    private Long id;
    /**
     * 账户id
     */
    private String accountId;
    /**
     * 密码
     */
    private String password;
    /**
     * 昵称
     */
    private String nickName;
    /**
     *当前账户是否被删除
     */
    @TableLogic
    private boolean deleted;

}
