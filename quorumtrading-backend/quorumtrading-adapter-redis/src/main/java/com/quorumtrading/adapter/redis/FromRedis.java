package com.quorumtrading.adapter.redis;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier to mark events received from Redis Pub/Sub.
 * Used to prevent re-publishing loops: events received from Redis
 * are fired with this qualifier so the RedisEventBridge doesn't
 * re-publish them back to Redis.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
public @interface FromRedis {
}
