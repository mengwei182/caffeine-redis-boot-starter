package org.example.event;

import lombok.Data;

import java.io.Serializable;
import java.time.Duration;

/**
 * @author lihui
 * @since 2024/1/12
 */
@Data
public class CacheEvent implements Serializable {
    private Object key;
    private Object value;
    private Duration duration;
    private String type;

    public CacheEvent(String type) {
        this.type = type;
    }

    public CacheEvent(Object key, String type) {
        this.key = key;
        this.type = type;
    }

    public CacheEvent(Object key, Object value, String type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public CacheEvent(Object key, Object value, Duration duration, String type) {
        this.key = key;
        this.value = value;
        this.duration = duration;
        this.type = type;
    }
}