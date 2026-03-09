package com.example.lazarus_backend00.service.subservice;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeatureMetadataManager {

    private final Map<Integer, String> idToNameMap = new ConcurrentHashMap<>();

    // 简单的策略枚举
    public enum NamingStrategy {
        DAILY_SHORT,    // yyyyMMdd.tif (ERA5)
        HOURLY_LONG     // name_yyyy-MM-dd-HH-mm-ss.tif (Meiji)
    }

    public FeatureMetadataManager() {
        // ERA5 (1xx)

        // ... 其他 ERA5

        // Meiji (2xx - 假设你分配了 ID)
        register(1, "salinity");
        register(2, "temp");
        register(3, "precip");
        register(4, "evap");
        register(5, "salinity05");
        // ... 其他 Meiji
    }

    private void register(int id, String name) {
        idToNameMap.put(id, name);
    }

    public String getFolderName(int featureId) {
        return idToNameMap.getOrDefault(featureId, "unknown");
    }

    /**
     * 根据特征名判断命名策略
     * (这里用简单的规则判断，实际项目可以配置在数据库里)
     */
    public NamingStrategy getStrategy(String featureName) {
        // 假设 ERA5 的特征名是固定的，或者 Meiji 的特征名有特定规律
        // 这里做一个简单的硬编码示例：
        if (featureName.equals("temperature") ||
                featureName.equals("precipitation") ||
                featureName.equals("sea_surface_temperature") ||
                featureName.equals("solar_radiation") ||
                featureName.startsWith("wind_")) {
            return NamingStrategy.DAILY_SHORT;
        } else {
            // 剩下的默认为 Meiji 格式
            return NamingStrategy.HOURLY_LONG;
        }
    }
}