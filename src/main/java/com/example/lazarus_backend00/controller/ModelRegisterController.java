package com.example.lazarus_backend00.controller;
import com.example.lazarus_backend00.service.ModelRegisterService;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/model")
@CrossOrigin(origins = "http://localhost:5173")  // 允许前端端口访问
public class ModelRegisterController {
    private final ModelRegisterService modelRegisterService;
    public ModelRegisterController(ModelRegisterService modelRegisterService) {
        this.modelRegisterService = modelRegisterService;
    }

    @PostMapping(
            value = "/regeister",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> parseFormData(
            @RequestPart("payload") String payloadJson,
            @RequestPart("modelFile") MultipartFile modelFile
    ) throws Exception {

        System.out.println("===== 接收到 multipart/form-data =====");

        // -------------------------
        // 1. 解析 JSON 字符串
        // -------------------------
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
        // -------------------------
        // 2. 接收 modelFile → byte[]
        // -------------------------
        byte[] modelBytes = modelFile.getBytes();
        System.out.println("✔ 接收到文件，大小 = " + modelBytes.length + " bytes");

        // -------------------------
        // 1. dynamicprocessmodel
        // -------------------------
        Map<String, Object> dpJson = (Map<String, Object>) payload.get("dynamicprocessmodel");

        DynamicProcessModelEntity model = new DynamicProcessModelEntity();
        model.setModelName((String) dpJson.get("modelName"));
        model.setModelSourcePaper((String) dpJson.get("modelSourcePaper"));
        model.setModelAuthor((String) dpJson.get("modelAuthor"));
        model.setModelSummary((String) dpJson.get("modelSummary"));
        model.setModelFile(modelBytes);

        System.out.println("✔ DynamicProcessModelEntity:");
        printObject(model);


        // -------------------------
        // 2. modelinterface
        // -------------------------
        Map<String, Object> interfaceJson = (Map<String, Object>) payload.get("modelinterface");

        ModelInterfaceEntity interfaceEntity = new ModelInterfaceEntity();
        interfaceEntity.setInterfaceName((String) interfaceJson.get("interfaceName"));
        interfaceEntity.setInterfaceSummary((String) interfaceJson.get("interfaceSummary"));
        interfaceEntity.setIsDefault((Boolean) interfaceJson.get("isDefault"));

        interfaceEntity.setProcessmodelId(null);
        interfaceEntity.setInterfaceType("default");

        System.out.println("\n✔ ModelInterfaceEntity:");
        printObject(interfaceEntity);


        // -------------------------
        // 3. parameters
        // -------------------------
        List<Map<String, Object>> parameterJsonList =
                (List<Map<String, Object>>) interfaceJson.get("parameters");

        List<ParameterEntity> parameterEntities = new ArrayList<>();
        List<List<FeatureEntity>> featureMatrix = new ArrayList<>(); // 每个 Parameter 对应一个 List<FeatureEntity>

        int paramIndex = 0;

        for (Map<String, Object> pJson : parameterJsonList) {

            ParameterEntity param = new ParameterEntity();

            param.setIoType((String) pJson.get("ioType"));
            param.setTemporalResolutionValue(toInt(pJson.get("temporalResolutionValue")));
            param.setTemporalResolutionUnit((String) pJson.get("temporalResolutionUnit"));
            param.setTemporalRangeValue(toInt(pJson.get("temporalRangeValue")));
            param.setTemporalRangeUnit((String) pJson.get("temporalRangeUnit"));

            param.setRowCount(toInt(pJson.get("rowCount")));
            param.setColumnCount(toInt(pJson.get("columnCount")));
            param.setZCount(toInt(pJson.get("zCount")));

            param.setSpatialResolutionX(toDouble(pJson.get("spatialResolutionX")));
            param.setSpatialResolutionY(toDouble(pJson.get("spatialResolutionY")));
            param.setSpatialResolutionZ(toDouble(pJson.get("spatialResolutionZ")));
            param.setSpatialResolutionUnit((String) pJson.get("spatialResolutionUnit"));

            param.setTensorOrder((String) pJson.get("tensorOrder"));

            param.setInterfaceId(null);

            parameterEntities.add(param);

            System.out.println("\n✔ ParameterEntity [" + paramIndex + "]");
            //printObject(param);

            // 处理每个 Parameter 对应的 features
            List<Map<String, Object>> featsJson = (List<Map<String, Object>>) pJson.get("features");
            List<FeatureEntity> featureList = new ArrayList<>();

            int featIndex = 0;
            for (Map<String, Object> fJson : featsJson) {
                FeatureEntity feature = new FeatureEntity();
                feature.setFeatureName((String) fJson.get("featureName"));
                feature.setDimensionIndex(toInt(fJson.get("dimensionIndex")));
                feature.setParameterId(null);

                featureList.add(feature);

                System.out.println("  ✔ FeatureEntity [" + featIndex + "]");

                featIndex++;
            }

            featureMatrix.add(featureList);
            paramIndex++;
        }

        // -------------------------
        // 打印 Parameter 和 Feature 矩阵
        // -------------------------
        System.out.println("===== ParameterEntities =====");
        for (int i = 0; i < parameterEntities.size(); i++) {
            System.out.println("✔ ParameterEntity [" + i + "]");
            printObject(parameterEntities.get(i));
        }

        System.out.println("===== Feature Matrix =====");
        for (int i = 0; i < featureMatrix.size(); i++) {
            System.out.println("Parameter [" + i + "] features:");
            List<FeatureEntity> feats = featureMatrix.get(i);
            for (int j = 0; j < feats.size(); j++) {
                System.out.println("  ✔ FeatureEntity [" + j + "]");
                printObject(feats.get(j));
            }
        }


        // -------------------------
        // 调用 Service 完成模型入库
        // -------------------------
        Integer processmodelId = modelRegisterService.registerModel(
                model, interfaceEntity, parameterEntities, featureMatrix
        );
        System.out.println("✔ 模型入库完成，processmodelId = " + processmodelId);
//
//        // 返回结构也改成二维 features
//        Map<String, Object> result = new LinkedHashMap<>();
//        result.put("model", model);
//        result.put("interface", interfaceEntity);
//        result.put("parameters", parameterEntities);
//        result.put("features", featureMatrix); // 二维列表
//        result.put("processmodelId", processmodelId);
        return ResponseEntity.ok("成功入库");
    }


    // -------------------------
    // 工具：打印对象所有属性
    // -------------------------
    private void printObject(Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                System.out.println("   " + field.getName() + " = " + field.get(obj));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -------------------------
    // 工具：类型转换
    // -------------------------
    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof String) return Integer.valueOf((String) obj);
        return null;
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.valueOf((String) obj);
        return null;
    }
}