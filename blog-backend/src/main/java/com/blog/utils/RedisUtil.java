package com.blog.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public void del(String key) {
        redisTemplate.delete(key);
    }

    public void del(Set<String> keys) {
        redisTemplate.delete(keys);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /** 批量取值：未命中返回 null 列表，按 key 顺序对应 */
    public List<Object> multiGet(java.util.Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return java.util.Collections.emptyList();
        return redisTemplate.opsForValue().multiGet(new java.util.LinkedHashSet<>(keys));
    }

    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    public void setList(String key, List<Object> list) {
        redisTemplate.opsForList().rightPushAll(key, list.toArray());
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.stream().map(clazz::cast).toList();
    }

    public Long getListSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public Object getListIndex(String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }

    public void setSet(String key, Set<Object> set) {
        redisTemplate.opsForSet().add(key, set.toArray());
    }

    public Set<Object> getSet(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean isSetMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long setAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Long setRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    public Object setPop(String key) {
        return redisTemplate.opsForSet().pop(key);
    }

    public void setHash(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object getHash(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public void setHashAll(String key, java.util.Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public java.util.Map<Object, Object> getHashAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Long getHashSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }
}