package org.example.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author lihui
 * @since 2024/1/12
 */
@Data
@AllArgsConstructor
public class CacheEven implements Serializable {
    private Object key;
    private String type;
}