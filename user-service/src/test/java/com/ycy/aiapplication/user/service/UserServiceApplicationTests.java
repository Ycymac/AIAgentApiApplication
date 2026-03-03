package com.ycy.aiapplication.user.service;

import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class UserServiceApplicationTests {
    @Autowired
    private UserAccountDOMapper userAccountDOMapper;
    @Test
    void contextLoads() {
        UserAccountDO userAccount = UserAccountDO.builder()
                .accountId("3253984909@qq.com")
                .password("ycy2006721")
                .nickName("ycy").build();
        userAccountDOMapper.insert(userAccount);

    }

}
