package com.example.lazarus_backend00;

import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class TestModelJsonGenerator {

    // 基础配置：分辨率 (度)
    // 1km 约等于 0.01 度
    private static final double RES_1KM = 0.01;
    // 10m 约等于 0.0001 度
    private static final double RES_10M = 0.0001;

    // 基础范围 (模拟南海)
    private static final double BASE_LON = 110.0;
    private static final double BASE_LAT = 10.0;
    // 网格数 (对应 Python 中的 50x50)
    private static final int COUNT_1KM = 50;

    public static void main(String[] args) throws IOException {
        List<String> payloads = new ArrayList<>();
        // 1. 南海气温模型
        payloads.add(generateSingleInputJson(
                "南海气温预测模型", "scs_air_temp.onnx",
                new String[]{"air_temp", "irradiance"}, new String[]{"air_temp"},
                12, 12, COUNT_1KM, RES_1KM, BASE_LON, BASE_LAT
        ));

        // 2. 降雨模型 (输出 4 个月)
        payloads.add(generateSingleInputJson(
                "南海降雨预测模型", "scs_rainfall.onnx",
                new String[]{"air_temp", "rainfall"}, new String[]{"rainfall"},
                12, 4, COUNT_1KM, RES_1KM, BASE_LON, BASE_LAT
        ));

        // 3. 海温模型 (SST)
        payloads.add(generateSingleInputJson(
                "南海海温预测模型", "scs_sst.onnx",
                new String[]{"air_temp", "rainfall", "irradiance"}, new String[]{"sst"},
                1, 1, COUNT_1KM, RES_1KM, BASE_LON, BASE_LAT
        ));

        // 4. 盐度模型 (5大块)
        for (int i = 1; i <= 5; i++) {
            // 简单偏移位置
            double lon = BASE_LON + (i * 0.5);
            double lat = BASE_LAT + (i * 0.5);
            payloads.add(generateSingleInputJson(
                    "盐度模型_Block_" + i, "salinity_block_" + i + ".onnx",
                    new String[]{"sst"}, new String[]{"salinity"},
                    1, 1, COUNT_1KM / 2, RES_1KM, lon, lat
            ));
        }

        // 5. NDVI 模型 (10个岛屿) - 特殊的双输入结构
        for (int i = 1; i <= 10; i++) {
            double lon = BASE_LON + (i * 0.2);
            double lat = BASE_LAT + (i * 0.2);
            payloads.add(generateNdviJson(
                    "岛屿NDVI模型_" + i, "island_ndvi_" + i + ".onnx",
                    lon, lat
            ));
        }

        // 6. 叶绿素模型 (10个不同范围)
        for (int i = 1; i <= 10; i++) {
            int count = COUNT_1KM - (i * 2);
            payloads.add(generateSingleInputJson(
                    "叶绿素模型_Scope_" + i, "chlorophyll_scope_" + i + ".onnx",
                    new String[]{"sst", "salinity", "chlorophyll"}, new String[]{"chlorophyll"},
                    4, 4, count, RES_1KM, BASE_LON, BASE_LAT
            ));
        }

//        // 打印输出
//        for (int i = 0; i < payloads.size(); i++) {
//            System.out.println("====== JSON Payload " + (i + 1) + " ======");
//            System.out.println(payloads.get(i));
//            System.out.println("\n");
//        }
        StringBuilder distinctJsonFile = new StringBuilder();
        distinctJsonFile.append("[\n");
        for (int i = 0; i < payloads.size(); i++) {
            distinctJsonFile.append(payloads.get(i));
            if (i < payloads.size() - 1) {
                distinctJsonFile.append(",\n");
            }
        }
        distinctJsonFile.append("\n]");

        // 写入文件
        String filePath = "D:\\CODE\\project\\Lazarus\\Lazarus-数据处理\\output\\models_config.json"; // 生成在项目根目录
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(distinctJsonFile.toString());
            System.out.println("✅ 成功生成配置文件: " + new File(filePath).getAbsolutePath());
        }
    }

    /**
     * 生成通用单输入参数的模型 JSON
     * 修改点：Keys 改为驼峰, 增加 version, isDefault 改为 default
     */
    private static String generateSingleInputJson(
            String name, String fileName,
            String[] inFeatures, String[] outFeatures,
            int inTime, int outTime,
            int gridCount, double res, double lon, double lat) {

        String inFeatStr = buildFeatureList(inFeatures);
        String outFeatStr = buildFeatureList(outFeatures);

        return String.format("""
        {
            "dynamicProcessModel": {
                "modelName": "%s",
                "modelSourcePaper": "自动生成测试",
                "modelAuthor": "TestScript",
                "modelSummary": "%s",
                "version": 1
            },
            "modelInterface": {
                "interfaceName": "StandardInterface",
                "interfaceSummary": "Auto-generated",
                "default": true,
                "parameters": [
                    {
                        "ioType": "INPUT",
                        "tensorOrder": 0,
                        "originPointLon": %f, "originPointLat": %f,
                        "axis": [
                            {"type": "TIME", "dimensionIndex": 1, "count": %d, "resolution": 1, "unit": "month"},
                            {"type": "SPACE_Y", "dimensionIndex": 3, "count": %d, "resolution": %.10f, "unit": "degree"},
                            {"type": "SPACE_X", "dimensionIndex": 4, "count": %d, "resolution": %.10f, "unit": "degree"}
                        ],
                        "features": %s
                    },
                    {
                        "ioType": "OUTPUT",
                        "tensorOrder": 0,
                        "originPointLon": %f, "originPointLat": %f,
                        "axis": [
                            {"type": "TIME", "dimensionIndex": 1, "count": %d, "resolution": 1, "unit": "month"},
                            {"type": "SPACE_Y", "dimensionIndex": 3, "count": %d, "resolution": %.10f, "unit": "degree"},
                            {"type": "SPACE_X", "dimensionIndex": 4, "count": %d, "resolution": %.10f, "unit": "degree"}
                        ],
                        "features": %s
                    }
                ]
            }
        }
        """, name, fileName,
                lon, lat, inTime, gridCount, res, gridCount, res, inFeatStr, // Input
                lon, lat, outTime, gridCount, res, gridCount, res, outFeatStr // Output
        );
    }

    /**
     * 特殊生成的 NDVI 模型 JSON (包含两个 INPUT 参数)
     * 修改点：Keys 改为驼峰, 增加 version, isDefault 改为 default
     */
    private static String generateNdviJson(String name, String fileName, double lon, double lat) {
        // 1km 网格 (Meteo)
        int countLow = 1;
        double resLow = RES_1KM;

        // 10m 网格 (NDVI) -> 分辨率高100倍
        int countHigh = 100;
        double resHigh = RES_10M;

        return String.format("""
        {
            "dynamicProcessModel": {
                "modelName": "%s",
                "modelSourcePaper": "NDVI融合测试",
                "modelAuthor": "TestScript",
                "modelSummary": "%s",
                "version": 1
            },
            "modelInterface": {
                "interfaceName": "DualInputInterface",
                "interfaceSummary": "Input1:Meteo(1km), Input2:NDVI(10m)",
                "default": true,
                "parameters": [
                    {
                        "ioType": "INPUT",
                        "tensorOrder": 0,
                        "originPointLon": %f, "originPointLat": %f,
                        "axis": [
                            {"type": "TIME", "dimensionIndex": 1, "count": 3, "resolution": 1, "unit": "month"},
                            {"type": "SPACE_Y", "dimensionIndex": 3, "count": %d, "resolution": %.10f, "unit": "degree"},
                            {"type": "SPACE_X", "dimensionIndex": 4, "count": %d, "resolution": %.10f, "unit": "degree"}
                        ],
                        "features": [ {"featureName": "irradiance"}, {"featureName": "rainfall"}, {"featureName": "air_temp"} ]
                    },
                    {
                        "ioType": "INPUT",
                        "tensorOrder": 1,
                        "originPointLon": %f, "originPointLat": %f,
                        "axis": [
                            {"type": "TIME", "dimensionIndex": 1, "count": 3, "resolution": 1, "unit": "month"},
                            {"type": "SPACE_Y", "dimensionIndex": 3, "count": %d, "resolution": %.10f, "unit": "degree"},
                            {"type": "SPACE_X", "dimensionIndex": 4, "count": %d, "resolution": %.10f, "unit": "degree"}
                        ],
                        "features": [ {"featureName": "ndvi"} ]
                    },
                    {
                        "ioType": "OUTPUT",
                        "tensorOrder": 0,
                        "originPointLon": %f, "originPointLat": %f,
                        "axis": [
                            {"type": "TIME", "dimensionIndex": 1, "count": 1, "resolution": 1, "unit": "month"},
                            {"type": "SPACE_Y", "dimensionIndex": 3, "count": %d, "resolution": %.10f, "unit": "degree"},
                            {"type": "SPACE_X", "dimensionIndex": 4, "count": %d, "resolution": %.10f, "unit": "degree"}
                        ],
                        "features": [ {"featureName": "ndvi"} ]
                    }
                ]
            }
        }
        """, name, fileName,
                lon, lat, countLow, resLow, countLow, resLow,   // Param 1 (Meteo 1km)
                lon, lat, countHigh, resHigh, countHigh, resHigh, // Param 2 (NDVI 10m)
                lon, lat, countHigh, resHigh, countHigh, resHigh  // Param 3 (Output 10m)
        );
    }

    private static String buildFeatureList(String[] feats) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < feats.length; i++) {
            sb.append(String.format("{ \"featureName\": \"%s\" }", feats[i]));
            if (i < feats.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}