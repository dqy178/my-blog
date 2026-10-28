package com.blog.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作封装。
 *
 * 当前项目实际用到的操作只有：字符串读写删、TTL 写入、key 存在性判断、自增。
 * 其余集合/哈希/列表操作未经使用，已移除 —— 需要时再按需补充。
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 写入并设置过期时间。
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时长
     * @param timeUnit 时长单位
     */
    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 读取键对应的值。
     *
     * @param key 键
     * @return 值，不存在时为 null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 判断键是否存在。
     *
     * <p>用于校验 token 是否仍在 Redis 中（登录态是否有效）。</p>
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除键。
     *
     * <p>键不存在时静默返回，不抛异常——缓存失效属于常规操作。</p>
     */
    public void del(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 自增并返回自增后的值。
     *
     * <p>用于浏览量趋势等计数场景，由 Redis 保证原子性。</p>
     */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 往 Set 里加入成员。
     *
     * <p>用于维护「用户 → 其所有 token」的索引集合。</p>
     */
    public void setAdd(String key, String member) {
        redisTemplate.opsForSet().add(key, member);
    }

    /**
     * 设置键的过期时间。
     *
     * <p>用于给集合类 key 设置 TTL，避免索引无限增长。</p>
     */
    public void expire(String key, long timeout, TimeUnit timeUnit) {
        redisTemplate.expire(key, timeout, timeUnit);
    }

    /**
     * 移除 Set 成员，返回是否真的移除了。
     *
     * <p>成员不存在时返回 false（{@code remove} 返回 null 或 0）。</p>
     */
    public Boolean setRemove(String key, String member) {
        Long removed = redisTemplate.opsForSet().remove(key, member);
        return removed != null && removed > 0;
    }

    /**
     * 读取 Set 的全部成员。
     *
     * <p>空集合返回空 Set 而非 null，调用方无需判空。</p>
     */
    public Set<String> setMembers(String key) {
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }
}
