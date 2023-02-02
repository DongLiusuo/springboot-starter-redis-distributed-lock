package org.example.lock.autoconfigure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.example.lock.aspect.DistributedLockAspect;
import org.example.lock.service.DistributedLockService;
import org.example.lock.service.impl.RedisDistributedLockServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author v_ECD963
 */
@Configuration
@ComponentScan(basePackages = "org.example.lock")
public class DistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = {"redisTemplate"})
    // @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance , ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        jackson2JsonRedisSerializer.setObjectMapper(om);
        //String类型的序列化
        RedisSerializer<?> stringSerializer = new StringRedisSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(stringSerializer);// key采用String序列化方式
        template.setValueSerializer(jackson2JsonRedisSerializer);// value序列化
        template.setHashKeySerializer(stringSerializer);// Hash key采用String序列化方式
        template.setHashValueSerializer(jackson2JsonRedisSerializer);// Hash value序列化
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public DistributedLockService redisDistributedLockService(RedisTemplate<String,Object> redisTemplate, ResourceLoader resourceLoader) {
        return new RedisDistributedLockServiceImpl(redisTemplate, resourceLoader);
    }

    @Bean
    @ConditionalOnBean(DistributedLockService.class)
    public DistributedLockAspect distributedLockAspect(@Qualifier("redisDistributedLockService") DistributedLockService distributedLockService) {
        return new DistributedLockAspect(distributedLockService);
    }
}
