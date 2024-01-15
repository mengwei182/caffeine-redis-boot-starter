package org.example.listener;

/**
 * @author lihui
 * @since 2024/1/15
 */
public interface ListenerChannel {
    String CACHE_CHANNEL = "cache_even_topic";
    String KEY_EXPIRATION_CHANNEL = "__keyevent@*__:expired";
}