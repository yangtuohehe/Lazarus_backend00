//package com.example.lazarus_backend00.service.impl;
//
//import com.example.lazarus_backend00.dao.*;
//import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
//import com.example.lazarus_backend00.domain.axis.Axis;
//import com.example.lazarus_backend00.domain.axis.Feature;
//import com.example.lazarus_backend00.component.container.Parameter;
//import com.example.lazarus_backend00.component.container.ModelContainer;
//import com.example.lazarus_backend00.component.container.ModelContainerFactory;
//import com.example.lazarus_backend00.service.ModelContainerProvider;
//import org.locationtech.jts.geom.Point;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//
///**
// * 模型容器构建服务实现类
// */
//@Service
//public class ModelContainerProviderImpl implements ModelContainerProvider {
//
//    private final DynamicProcessModelDao modelDao;
//    private final ModelInterfaceDao interfaceDao;
//    private final ParameterDao parameterDao;
//    private final FeatureDao featureDao;
//    private final FeatureParameterDao featureParameterDao;
//    private final SpaceAxisXDao xAxisDao;
//    private final SpaceAxisYDao yAxisDao;
//    private final SpaceAxisZDao zAxisDao;
//    private final TimeAxisDao timeAxisDao;
//    private final ModelContainerFactory containerFactory;
//
//    public ModelContainerProviderImpl(
//            DynamicProcessModelDao modelDao,
//            ModelInterfaceDao interfaceDao,
//            ParameterDao parameterDao,
//            FeatureDao featureDao,
//            FeatureParameterDao featureParameterDao,
//            SpaceAxisXDao xAxisDao,
//            SpaceAxisYDao yAxisDao,
//            SpaceAxisZDao zAxisDao,
//            TimeAxisDao timeAxisDao,
//            ModelContainerFactory containerFactory) {
//        this.modelDao = modelDao;
//        this.interfaceDao = interfaceDao;
//        this.parameterDao = parameterDao;
//        this.featureDao = featureDao;
//        this.featureParameterDao = featureParameterDao;
//        this.xAxisDao = xAxisDao;
//        this.yAxisDao = yAxisDao;
//        this.zAxisDao = zAxisDao;
//        this.timeAxisDao = timeAxisDao;
//        this.containerFactory = containerFactory;
//    }
//
//    @Override
//    public ModelContainer reconstructContainer(Integer modelId) {
//        // Step 1: 查模型元数据
//        DynamicProcessModelEntity modelEntity = modelDao.selectById(modelId);
//        if (modelEntity == null) {
//            throw new IllegalArgumentException("模型不存在 ID: " + modelId);
//        }
//
//        // Step 2: 查接口 (获取默认接口)
//        ModelInterfaceEntity interfaceQuery = new ModelInterfaceEntity();
//        interfaceQuery.setProcessmodelId(modelId);
//        List<ModelInterfaceEntity> interfaces = interfaceDao.selectByCondition(interfaceQuery);
//
//        ModelInterfaceEntity targetInterface = interfaces.stream()
//                .filter(i -> Boolean.TRUE.equals(i.getIsDefault()))
//                .findFirst()
//                .orElse(interfaces.isEmpty() ? null : interfaces.get(0));
//
//        if (targetInterface == null) {
//            throw new IllegalStateException("模型 ID=" + modelId + " 未定义任何接口，无法构建容器");
//        }
//
//        // Step 3: 组装 Parameter 业务对象
//        List<Parameter> domainParameters = assembleDomainParameters(targetInterface.getId());
//
//        // Step 4: 智能识别引擎类型 (修复了之前的 Bug)
//        String engineType = determineEngineType(modelEntity);
//
//        // Step 5: 召唤工厂实例化
//        // 你的工厂要求 engineType 必填，所以这里传入探测结果
//        return containerFactory.createContainer(
//                modelId,
//                modelEntity.getVersion(),
//                engineType,
//                domainParameters
//        );
//    }
//
//    /**
//     * 强类型校验的引擎识别逻辑
//     * 规则：
//     * 1. 打印 ID 用于调试。
//     * 2. 查库获取文件头魔数 (Magic Number)。
//     * 3. 优先信赖二进制魔数，其次信赖文件名。
//     * 4. 无法识别时，直接抛异常阻断流程。
//     */
//    public String determineEngineType(DynamicProcessModelEntity model) {
//        Integer id = model.getId();
//        System.out.println(">>> 开始嗅探模型引擎类型，当前模型 ID: " + id);
//
//        if (id == null) {
//            throw new IllegalArgumentException("严重错误：模型 ID 为空，无法进行嗅探！");
//        }
//
//        byte[] header = null;
//        try {
//            // 1. 获取 Entity (这里已经通了，非常棒)
//            DynamicProcessModelEntity headerEntity = modelDao.selectModelHeader(id);
//            if (headerEntity != null) {
//                header = headerEntity.getModelFile();
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("数据库查询文件头失败，ID: " + id, e);
//        }
//
//        if (header == null || header.length < 2) {
//            throw new IllegalStateException("严重错误：数据库中模型文件为空或损坏 (Header < 2 bytes)，ID: " + id);
//        }
//
//        // --- 核心判断逻辑升级 ---
//
//        // A. 判断 PyTorch (Zip 格式)
//        // 魔数: 50 4B (PK..)
//        if (header[0] == 0x50 && header[1] == 0x4B) {
//            System.out.println(">>> 嗅探结果: 检测到 ZIP 魔数，判定为 DJL_PYTORCH");
//            return ModelContainerFactory.ENGINE_DJL_TORCH;
//        }
//
//        // B. 判断 ONNX (新增 Protobuf 魔数支持)
//        // 🔥 新增规则：只要以 0x08 开头，就认为是 ONNX (Protobuf)
//        if (header[0] == 0x08) {
//            System.out.println(">>> 嗅探结果: 检测到 Protobuf/ONNX 魔数 (0x08)，判定为 ONNX");
//            return ModelContainerFactory.ENGINE_ONNX;
//        }
//
//        // C. 判断 ONNX (文件名后缀兜底)
//        // 防止某些特殊 ONNX 文件不以 08 开头，但文件名是对的
//        String modelName = model.getModelName();
//        if (modelName != null) {
//            String lower = modelName.toLowerCase();
//            if (lower.endsWith(".onnx")) {
//                System.out.println(">>> 嗅探结果: 非 0x08 开头但后缀为 .onnx，判定为 ONNX");
//                return ModelContainerFactory.ENGINE_ONNX;
//            }
//        }
//
//        // D. 还是识别不了 -> 报错
//        String hexHeader = String.format("%02X %02X", header[0], header[1]);
//        throw new UnsupportedOperationException(
//                String.format("无法识别的模型类型！ID=%d, 文件名=%s, 文件头Hex=[%s]。当前系统仅支持 PyTorch(Zip) 和 ONNX(0x08/后缀)。",
//                        id, modelName, hexHeader)
//        );
//    }
//
//    private List<Parameter> assembleDomainParameters(Integer interfaceId) {
//        List<Parameter> result = new ArrayList<>();
//        ParameterEntity query = new ParameterEntity();
//        query.setInterfaceId(interfaceId);
//        List<ParameterEntity> entities = parameterDao.selectByCondition(query);
//
//        for (ParameterEntity entity : entities) {
//            List<Axis> axisList = fetchAxes(entity.getId());
//            List<Feature> featureList = fetchFeatures(entity.getId());
//
//            Point origin = null;
//            if (entity.getOriginPoint() instanceof Point) {
//                origin = (Point) entity.getOriginPoint();
//            }
//
//            Parameter domainParam = new Parameter(
//                    entity.getIoType(),
//                    entity.getTensorOrder(),
//                    origin,
//                    entity.getCoverageGeom(),
//                    axisList,
//                    featureList
//            );
//            result.add(domainParam);
//        }
//        return result;
//    }
//
//    private List<Axis> fetchAxes(Integer paramId) {
//        List<Axis> axes = new ArrayList<>();
//
//        SpaceAxisXEntity x = xAxisDao.selectByParameterId(paramId);
//        if (x != null) axes.add(x);
//        SpaceAxisYEntity y = yAxisDao.selectByParameterId(paramId);
//        if (y != null) axes.add(y);
//        SpaceAxisZEntity z = zAxisDao.selectByParameterId(paramId);
//        if (z != null) axes.add(z);
//        TimeAxisEntity t = timeAxisDao.selectByParameterId(paramId);
//        if (t != null) axes.add(t);
//
//        axes.sort(Comparator.comparing(Axis::getDimensionIndex, Comparator.nullsLast(Integer::compareTo)));
//        return axes;
//    }
//
//    private List<Feature> fetchFeatures(Integer paramId) {
//        List<Feature> features = new ArrayList<>();
//        FeatureParameterEntity query = new FeatureParameterEntity();
//        query.setParameterId(paramId);
//        List<FeatureParameterEntity> relations = featureParameterDao.selectByDynamicAttributes(query);
//        relations.sort(Comparator.comparing(FeatureParameterEntity::getFeatureLayer));
//        for (FeatureParameterEntity rel : relations) {
//            FeatureEntity fEntity = featureDao.selectById(rel.getFeatureId());
//            if (fEntity != null) {
//                features.add(new Feature(fEntity.getId(), fEntity.getFeatureName()));
//            }
//        }
//        return features;
//    }
//}
package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.*;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.*;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.ModelContainerFactory;
import com.example.lazarus_backend00.service.ModelContainerProvider;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ModelContainerProviderImpl implements ModelContainerProvider {

    private final DynamicProcessModelDao modelDao;
    private final ModelInterfaceDao interfaceDao;
    private final ParameterDao parameterDao;
    private final FeatureDao featureDao;
    private final FeatureParameterDao featureParameterDao;
    private final SpaceAxisXDao xAxisDao;
    private final SpaceAxisYDao yAxisDao;
    private final SpaceAxisZDao zAxisDao;
    private final TimeAxisDao timeAxisDao;
    private final ModelContainerFactory containerFactory;

    public ModelContainerProviderImpl(DynamicProcessModelDao modelDao, ModelInterfaceDao interfaceDao, ParameterDao parameterDao, FeatureDao featureDao, FeatureParameterDao featureParameterDao, SpaceAxisXDao xAxisDao, SpaceAxisYDao yAxisDao, SpaceAxisZDao zAxisDao, TimeAxisDao timeAxisDao, ModelContainerFactory containerFactory) {
        this.modelDao = modelDao;
        this.interfaceDao = interfaceDao;
        this.parameterDao = parameterDao;
        this.featureDao = featureDao;
        this.featureParameterDao = featureParameterDao;
        this.xAxisDao = xAxisDao;
        this.yAxisDao = yAxisDao;
        this.zAxisDao = zAxisDao;
        this.timeAxisDao = timeAxisDao;
        this.containerFactory = containerFactory;
    }

    @Override
    public ModelContainer reconstructContainer(Integer modelId) {
        DynamicProcessModelEntity modelEntity = modelDao.selectById(modelId);
        if (modelEntity == null) throw new IllegalArgumentException("Model not found ID: " + modelId);

        ModelInterfaceEntity interfaceQuery = new ModelInterfaceEntity();
        interfaceQuery.setProcessmodelId(modelId);
        List<ModelInterfaceEntity> interfaces = interfaceDao.selectByCondition(interfaceQuery);

        ModelInterfaceEntity targetInterface = interfaces.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsDefault()))
                .findFirst()
                .orElse(interfaces.isEmpty() ? null : interfaces.get(0));

        if (targetInterface == null) throw new IllegalStateException("No interface for model ID=" + modelId);

        List<Parameter> domainParameters = assembleDomainParameters(targetInterface.getId());
        String engineType = determineEngineType(modelEntity);

        return containerFactory.createContainer(modelId, modelEntity.getVersion(), engineType, domainParameters);
    }

    public String determineEngineType(DynamicProcessModelEntity model) {
        Integer id = model.getId();
        byte[] header = null;
        try {
            DynamicProcessModelEntity headerEntity = modelDao.selectModelHeader(id);
            if (headerEntity != null) header = headerEntity.getModelFile();
        } catch (Exception e) { throw new RuntimeException("DB query header failed ID: " + id, e); }

        if (header == null || header.length < 2) throw new IllegalStateException("Empty/corrupt model file ID: " + id);

        if (header[0] == 0x50 && header[1] == 0x4B) return ModelContainerFactory.ENGINE_DJL_TORCH;
        if (header[0] == 0x08) return ModelContainerFactory.ENGINE_ONNX;
        if (model.getModelName() != null && model.getModelName().toLowerCase().endsWith(".onnx")) return ModelContainerFactory.ENGINE_ONNX;

        throw new UnsupportedOperationException("Unsupported engine for ID=" + id);
    }

    private List<Parameter> assembleDomainParameters(Integer interfaceId) {
        List<Parameter> result = new ArrayList<>();
        ParameterEntity query = new ParameterEntity();
        query.setInterfaceId(interfaceId);
        List<ParameterEntity> entities = parameterDao.selectByCondition(query);

        for (ParameterEntity entity : entities) {
            List<Axis> axisList = fetchAxes(entity.getId());
            List<Feature> featureList = fetchFeatures(entity.getId());
            Point origin = (entity.getOriginPoint() instanceof Point) ? (Point) entity.getOriginPoint() : null;

            result.add(new Parameter(entity.getIoType(), entity.getTensorOrder(), origin, entity.getCoverageGeom(), axisList, featureList));
        }
        return result;
    }

    private List<Axis> fetchAxes(Integer paramId) {
        List<Axis> axes = new ArrayList<>();

        // ✅ 核心修正：手动将 Entity 属性拷贝至真正的 Domain 类
        SpaceAxisXEntity xEntity = xAxisDao.selectByParameterId(paramId);
        if (xEntity != null) {
            SpaceAxisX x = new SpaceAxisX();
            copyBase(xEntity, x);
            axes.add(x);
        }

        SpaceAxisYEntity yEntity = yAxisDao.selectByParameterId(paramId);
        if (yEntity != null) {
            SpaceAxisY y = new SpaceAxisY();
            copyBase(yEntity, y);
            axes.add(y);
        }

        SpaceAxisZEntity zEntity = zAxisDao.selectByParameterId(paramId);
        if (zEntity != null) {
            SpaceAxisZ z = new SpaceAxisZ();
            copyBase(zEntity, z);
            axes.add(z);
        }

        TimeAxisEntity tEntity = timeAxisDao.selectByParameterId(paramId);
        if (tEntity != null) {
            TimeAxis t = new TimeAxis();
            copyBase(tEntity, t);
            t.setType("TIME"); // 必须显式设置类型
            axes.add(t);
        }

        axes.sort(Comparator.comparing(Axis::getDimensionIndex, Comparator.nullsLast(Integer::compareTo)));
        return axes;
    }

    private void copyBase(Axis src, Axis target) {
        target.setType(src.getType());
        target.setDimensionIndex(src.getDimensionIndex());
        target.setCount(src.getCount());
        target.setResolution(src.getResolution());
        target.setUnit(src.getUnit());
    }

    private List<Feature> fetchFeatures(Integer paramId) {
        List<Feature> features = new ArrayList<>();
        FeatureParameterEntity query = new FeatureParameterEntity();
        query.setParameterId(paramId);
        List<FeatureParameterEntity> relations = featureParameterDao.selectByDynamicAttributes(query);
        relations.sort(Comparator.comparing(FeatureParameterEntity::getFeatureLayer));
        for (FeatureParameterEntity rel : relations) {
            FeatureEntity fEntity = featureDao.selectById(rel.getFeatureId());
            if (fEntity != null) features.add(new Feature(fEntity.getId(), fEntity.getFeatureName()));
        }
        return features;
    }
}