package org.example.event;

/**
 * @author lihui
 * @since 2024/1/12
 */
public enum CacheEventEnum {
    /**
     * 更新指定key
     */
    UPDATE_KEY,
    /**
     * 删除指定key
     */
    EVICT_KEY,
    /**
     * 删除全部key
     */
    CLEAR
}