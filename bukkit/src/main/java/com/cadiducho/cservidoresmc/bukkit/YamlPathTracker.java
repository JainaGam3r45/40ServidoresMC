package com.cadiducho.cservidoresmc.bukkit;

import java.util.Map;
import java.util.TreeMap;

class YamlPathTracker {

    private final TreeMap<Integer, String> headers = new TreeMap<>();

    String pathFor(int indent, String key) {
        StringBuilder path = new StringBuilder();
        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            if (entry.getKey() >= indent) {
                continue;
            }
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(entry.getValue());
        }
        if (path.length() > 0) {
            path.append('.');
        }
        path.append(key);
        return path.toString();
    }

    void put(int indent, String key) {
        headers.tailMap(indent, true).clear();
        headers.put(indent, key);
    }
}
