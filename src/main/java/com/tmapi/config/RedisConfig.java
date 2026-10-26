package com.tmapi.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration with per-cache TTL tuning.
 * Uses JSON serialization (not Java serialization) for human-readable cache values
 * and forward-compatibility with class refactors.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.cache.redis.time-to-live:300000}")
    private long defaultTtlMs;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(genericJsonSerializer());
        template.setHashValueSerializer(genericJsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // Per-cache TTL strategy
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
            CacheNames.TASKS,         cacheConfig(Duration.ofMinutes(5)),
            CacheNames.TASK_LIST,     cacheConfig(Duration.ofMinutes(2)),
            CacheNames.WORKSPACES,    cacheConfig(Duration.ofMinutes(10)),
            CacheNames.WORKSPACE_MEMBERS, cacheConfig(Duration.ofMinutes(10)),
            CacheNames.USER_PROFILE,  cacheConfig(Duration.ofMinutes(15))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(cacheConfig(Duration.ofMillis(defaultTtlMs)))
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    private RedisCacheConfiguration cacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericJsonSerializer()));
    }

    private GenericJackson2JsonRedisSerializer genericJsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(om);
    }

    /**
     * Centralized cache name constants — avoids magic strings scattered across the codebase.
     */
    public static final class CacheNames {
        public static final String TASKS              = "tasks";
        public static final String TASK_LIST          = "task_list";
        public static final String WORKSPACES         = "workspaces";
        public static final String WORKSPACE_MEMBERS  = "workspace_members";
        public static final String USER_PROFILE       = "user_profile";

        private CacheNames() {}
    }
}
