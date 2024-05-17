package org.example.listener;

/**
 * @author lihui
 * @since 2024/1/15
 */
public interface Topic {
    String CACHE_CHANNEL = "__cache_even_topic";
    String KEY_EXPIRATION_CHANNEL = "__keyevent@*__:expired";
}