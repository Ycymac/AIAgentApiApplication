package com.ycy.aiapplication.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ycy.aiapplication.dao.mapper.InterviewRecordDOMapper;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.mapper.MapperFactoryBean;

import java.util.Date;

@Configuration
public class DataBaseConfiguration {
    /**
     * MyBatis-Plus分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    /**
     * Mybatis-Plus 源数据自动填充类
     */
    @Bean
    public MyMetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }

    @Bean
    public MapperFactoryBean<InterviewRecordDOMapper> interviewRecordDOMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<InterviewRecordDOMapper> factoryBean = new MapperFactoryBean<>(InterviewRecordDOMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }

    @Bean
    public MapperFactoryBean<UserAccountDOMapper> userAccountDOMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<UserAccountDOMapper> factoryBean = new MapperFactoryBean<>(UserAccountDOMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }


    static class MyMetaObjectHandler implements MetaObjectHandler {
        @Override
        public void insertFill(MetaObject metaObject) {
            //创建当前时间并进行填充
            strictInsertFill(metaObject, "createTime", Date::new, Date.class);
            //自动填充 false
            strictInsertFill(metaObject, "deleted",()->false , Boolean.class);
        }


        @Override
        public void updateFill(MetaObject metaObject) {
            //当前没有带有需要修改的属性
        }


    }
}
