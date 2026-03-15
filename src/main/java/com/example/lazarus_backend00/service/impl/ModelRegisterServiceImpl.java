//package com.example.lazarus_backend00.service.impl;
//
//import com.example.lazarus_backend00.dao.*;
//import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
//import com.example.lazarus_backend00.domain.axis.Axis; // 这里的 Axis 是你定义的 Entity 父类/接口
//import com.example.lazarus_backend00.service.ModelRegisterService;
//import org.locationtech.jts.geom.*;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//public class ModelRegisterServiceImpl implements ModelRegisterService {
//
//    private final DynamicProcessModelDao modelDao;
//    private final ModelInterfaceDao interfaceDao;
//    private final ParameterDao parameterDao;
//    private final FeatureDao featureDao;
//    private final FeatureParameterDao featureParameterDao;
//
//    // 轴 DAO
//    private final SpaceAxisXDao xAxisDao;
//    private final SpaceAxisYDao yAxisDao;
//    private final SpaceAxisZDao zAxisDao;
//    private final TimeAxisDao timeAxisDao;
//
//    // JTS 工厂
//    private final GeometryFactory gf = new GeometryFactory();
//
//    public ModelRegisterServiceImpl(
//            DynamicProcessModelDao modelDao,
//            ModelInterfaceDao interfaceDao,
//            ParameterDao parameterDao,
//            FeatureDao featureDao,
//            FeatureParameterDao featureParameterDao,
//            SpaceAxisXDao xAxisDao,
//            SpaceAxisYDao yAxisDao,
//            SpaceAxisZDao zAxisDao,
//            TimeAxisDao timeAxisDao) {
//        this.modelDao = modelDao;
//        this.interfaceDao = interfaceDao;
//        this.parameterDao = parameterDao;
//        this.featureDao = featureDao;
//        this.featureParameterDao = featureParameterDao;
//        this.xAxisDao = xAxisDao;
//        this.yAxisDao = yAxisDao;
//        this.zAxisDao = zAxisDao;
//        this.timeAxisDao = timeAxisDao;
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Integer registerModel(
//            DynamicProcessModelEntity processModel,
//            ModelInterfaceEntity modelInterface,
//            List<ParameterEntity> parameters,
//            List<List<FeatureEntity>> features,
//            List<List<Axis>> axis) {
//
//        LocalDateTime now = LocalDateTime.now();
//        processModel.setId(null);
//        processModel.setCreatedAt(now);
//        processModel.setUpdatedAt(now);
//        modelDao.insert(processModel);
//
//        addInterfaceToModel(processModel.getId(), modelInterface, parameters, features, axis);
//        return processModel.getId();
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Integer addInterfaceToModel(
//            Integer processmodelId,
//            ModelInterfaceEntity modelInterface,
//            List<ParameterEntity> parameters,
//            List<List<FeatureEntity>> features,
//            List<List<Axis>> axis) {
//
//        LocalDateTime now = LocalDateTime.now();
//        modelInterface.setId(null);
//        modelInterface.setProcessmodelId(processmodelId);
//        modelInterface.setCreatedAt(now);
//        modelInterface.setUpdatedAt(now);
//        if (modelInterface.getIsDefault() == null) modelInterface.setIsDefault(false);
//        interfaceDao.insert(modelInterface);
//        Integer interfaceId = modelInterface.getId();
//
//        if (parameters != null) {
//            for (int i = 0; i < parameters.size(); i++) {
//                ParameterEntity paramEntity = parameters.get(i);
//                List<FeatureEntity> paramFeatures = (features != null && i < features.size()) ? features.get(i) : null;
//                List<Axis> paramAxes = (axis != null && i < axis.size()) ? axis.get(i) : null;
//
//                saveParameterTree(paramEntity, interfaceId, paramFeatures, paramAxes, now);
//            }
//        }
//        return interfaceId;
//    }
//
//    private void saveParameterTree(
//            ParameterEntity paramEntity,
//            Integer interfaceId,
//            List<FeatureEntity> features,
//            List<Axis> axes,
//            LocalDateTime now) {
//
//        // 🌟 核心逻辑：直接在这里计算 Geometry 并填入 Entity
//        // 不需要任何 Domain 类参与
//        Geometry calculatedCoverage = calculateCoverageFromEntities(paramEntity.getOriginPoint(), axes);
//        paramEntity.setCoverageGeom(calculatedCoverage);
//
//        // 入库
//        paramEntity.setId(null);
//        paramEntity.setInterfaceId(interfaceId);
//        paramEntity.setCreatedAt(now);
//        paramEntity.setUpdatedAt(now);
//        parameterDao.insert(paramEntity);
//
//        Integer paramId = paramEntity.getId();
//
//        // 存特征
//        if (features != null) {
//            for (int layer = 0; layer < features.size(); layer++) {
//                FeatureEntity f = features.get(layer);
//                if (f != null) saveFeatureRelation(f, paramId, layer, now);
//            }
//        }
//
//        // 存轴
//        if (axes != null) {
//            for (Axis a : axes) {
//                if (a != null) saveAxis(a, paramId, now);
//            }
//        }
//    }
//
//    /**
//     * 🌟 私有工具方法：仅使用 Entity 数据进行几何计算
//     */
//    private Geometry calculateCoverageFromEntities(Geometry originGeo, List<Axis> axes) {
//        if (originGeo == null || !(originGeo instanceof Point)) {
//            return originGeo; // 如果没原点，就没法算范围
//        }
//        Point origin = (Point) originGeo;
//
//        // 临时变量提取轴信息
//        Integer xCount = null; Double xRes = null;
//        Integer yCount = null; Double yRes = null;
//        Integer zCount = null; Double zRes = null;
//
//        if (axes != null) {
//            for (Axis axis : axes) {
//                // 判断类型并提取 Count 和 Resolution
//                if (axis instanceof SpaceAxisXEntity) {
//                    xCount = ((SpaceAxisXEntity) axis).getCount();
//                    xRes = ((SpaceAxisXEntity) axis).getResolution();
//                } else if (axis instanceof SpaceAxisYEntity) {
//                    yCount = ((SpaceAxisYEntity) axis).getCount();
//                    yRes = ((SpaceAxisYEntity) axis).getResolution();
//                } else if (axis instanceof SpaceAxisZEntity) {
//                    zCount = ((SpaceAxisZEntity) axis).getCount();
//                    zRes = ((SpaceAxisZEntity) axis).getResolution();
//                }
//            }
//        }
//
//        // 如果没有 XY 轴，退化为点
//        if (xCount == null || yCount == null || xRes == null || yRes == null) {
//            return origin;
//        }
//
//        // 计算边界
//        Coordinate c = origin.getCoordinate();
//        double minX = c.getX();
//        double minY = c.getY();
//        double maxX = minX + (xCount * xRes);
//        double maxY = minY + (yCount * yRes);
//
//        // 3D 情况
//        if (zCount != null && zRes != null) {
//            double minZ = Double.isNaN(c.getZ()) ? 0.0 : c.getZ();
//            double maxZ = minZ + (zCount * zRes);
//
//            Coordinate[] coords3D = new Coordinate[]{
//                    new Coordinate(minX, minY, minZ),
//                    new Coordinate(minX, maxY, maxZ),
//                    new Coordinate(maxX, maxY, maxZ),
//                    new Coordinate(maxX, minY, minZ),
//                    new Coordinate(minX, minY, minZ)
//            };
//            Polygon p = gf.createPolygon(coords3D);
//            p.setSRID(4326);
//            return p;
//        }
//        // 2D 情况
//        else {
//            Coordinate[] coords2D = new Coordinate[]{
//                    new Coordinate(minX, minY),
//                    new Coordinate(minX, maxY),
//                    new Coordinate(maxX, maxY),
//                    new Coordinate(maxX, minY),
//                    new Coordinate(minX, minY)
//            };
//            Polygon p = gf.createPolygon(coords2D);
//            p.setSRID(4326);
//            return p;
//        }
//    }
//
//    private void saveFeatureRelation(FeatureEntity feature, Integer paramId, int layerIndex, LocalDateTime now) {
//        Integer featureId = feature.getId();
//        if (featureId == null) {
//            feature.setCreatedAt(now);
//            feature.setUpdatedAt(now);
//            featureDao.insert(feature);
//            featureId = feature.getId();
//        }
//        FeatureParameterEntity relation = new FeatureParameterEntity();
//        relation.setParameterId(paramId);
//        relation.setFeatureId(featureId);
//        relation.setFeatureLayer(layerIndex);
//        featureParameterDao.insert(relation);
//    }
//
//    private void saveAxis(Axis axis, Integer paramId, LocalDateTime now) {
//        // ... (保持之前的 saveAxis 多态插入逻辑，此处省略重复代码) ...
//        // 确保在这里正确调用 xAxisDao.insert 等
//        if (axis instanceof SpaceAxisXEntity) {
//            SpaceAxisXEntity e = (SpaceAxisXEntity) axis;
//            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
//            xAxisDao.insert(e);
//        } else if (axis instanceof SpaceAxisYEntity) {
//            SpaceAxisYEntity e = (SpaceAxisYEntity) axis;
//            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
//            yAxisDao.insert(e);
//        } else if (axis instanceof SpaceAxisZEntity) {
//            SpaceAxisZEntity e = (SpaceAxisZEntity) axis;
//            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
//            zAxisDao.insert(e);
//        } else if (axis instanceof TimeAxisEntity) {
//            TimeAxisEntity e = (TimeAxisEntity) axis;
//            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
//            timeAxisDao.insert(e);
//        }
//    }
//}

package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.*;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.service.ModelRegisterService;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.geotools.referencing.GeodeticCalculator;
import java.awt.geom.Point2D;
@Service
public class ModelRegisterServiceImpl implements ModelRegisterService {

    private final DynamicProcessModelDao modelDao;
    private final ModelInterfaceDao interfaceDao;
    private final ParameterDao parameterDao;
    private final FeatureDao featureDao;
    private final FeatureParameterDao featureParameterDao;

    // 轴 DAO
    private final SpaceAxisXDao xAxisDao;
    private final SpaceAxisYDao yAxisDao;
    private final SpaceAxisZDao zAxisDao;
    private final TimeAxisDao timeAxisDao;

    // JTS 工厂
    private final GeometryFactory gf = new GeometryFactory();

    public ModelRegisterServiceImpl(
            DynamicProcessModelDao modelDao,
            ModelInterfaceDao interfaceDao,
            ParameterDao parameterDao,
            FeatureDao featureDao,
            FeatureParameterDao featureParameterDao,
            SpaceAxisXDao xAxisDao,
            SpaceAxisYDao yAxisDao,
            SpaceAxisZDao zAxisDao,
            TimeAxisDao timeAxisDao) {
        this.modelDao = modelDao;
        this.interfaceDao = interfaceDao;
        this.parameterDao = parameterDao;
        this.featureDao = featureDao;
        this.featureParameterDao = featureParameterDao;
        this.xAxisDao = xAxisDao;
        this.yAxisDao = yAxisDao;
        this.zAxisDao = zAxisDao;
        this.timeAxisDao = timeAxisDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer registerModel(
            DynamicProcessModelEntity processModel,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features,
            List<List<Axis>> axis) {

        LocalDateTime now = LocalDateTime.now();
        processModel.setId(null);
        processModel.setCreatedAt(now);
        processModel.setUpdatedAt(now);
        modelDao.insert(processModel);

        addInterfaceToModel(processModel.getId(), modelInterface, parameters, features, axis);
        return processModel.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addInterfaceToModel(
            Integer processmodelId,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features,
            List<List<Axis>> axis) {

        LocalDateTime now = LocalDateTime.now();
        modelInterface.setId(null);
        modelInterface.setProcessmodelId(processmodelId);
        modelInterface.setCreatedAt(now);
        modelInterface.setUpdatedAt(now);
        if (modelInterface.getIsDefault() == null) modelInterface.setIsDefault(false);
        interfaceDao.insert(modelInterface);
        Integer interfaceId = modelInterface.getId();

        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                ParameterEntity paramEntity = parameters.get(i);
                List<FeatureEntity> paramFeatures = (features != null && i < features.size()) ? features.get(i) : null;
                List<Axis> paramAxes = (axis != null && i < axis.size()) ? axis.get(i) : null;

                saveParameterTree(paramEntity, interfaceId, paramFeatures, paramAxes, now);
            }
        }
        return interfaceId;
    }

    private void saveParameterTree(
            ParameterEntity paramEntity,
            Integer interfaceId,
            List<FeatureEntity> features,
            List<Axis> axes,
            LocalDateTime now) {

        // 1. 计算空间覆盖范围
        Geometry calculatedCoverage = calculateCoverageFromEntities(paramEntity.getOriginPoint(), axes);
        paramEntity.setCoverageGeom(calculatedCoverage);

        // 2. 参数入库
        paramEntity.setId(null);
        paramEntity.setInterfaceId(interfaceId);
        paramEntity.setCreatedAt(now);
        paramEntity.setUpdatedAt(now);
        parameterDao.insert(paramEntity);

        Integer paramId = paramEntity.getId();

        // 3. 计算 Feature 在 Tensor 中的维度 Index
        // 规则：0是BatchSize，根据axes占用的位置，找出第一个空缺的正整数
        Integer featureDimIndex = calculateFeatureDimensionIndex(axes);

        // 4. 存特征 (并写入 calculated dimensionIndex)
        if (features != null) {
            for (int layer = 0; layer < features.size(); layer++) {
                FeatureEntity f = features.get(layer);
                if (f != null) {
                    // 传入 featureDimIndex
                    saveFeatureRelation(f, paramId, layer, featureDimIndex, now);
                }
            }
        }

        // 5. 存轴
        if (axes != null) {
            for (Axis a : axes) {
                if (a != null) saveAxis(a, paramId, now);
            }
        }
    }

    /**
     * 🔥 核心新增逻辑：计算特征轴维度
     */
    private Integer calculateFeatureDimensionIndex(List<Axis> axes) {
        if (axes == null || axes.isEmpty()) {
            return 1; // 0是Batch, 如果没有其他轴，Feature就是1
        }

        // 1. 收集所有已被占用的维度索引
        Set<Integer> occupiedIndices = new HashSet<>();

        // BatchSize 永远占用 0
        occupiedIndices.add(0);

        // 遍历轴列表，提取它们占用的 Index
        for (Axis axis : axes) {
            Integer idx = null;
            // 根据 Entity 类型提取 dimensionIndex
            // 这里假设你的 Axis 实体类都有 getDimensionIndex() 方法
            if (axis instanceof SpaceAxisXEntity) idx = ((SpaceAxisXEntity) axis).getDimensionIndex();
            else if (axis instanceof SpaceAxisYEntity) idx = ((SpaceAxisYEntity) axis).getDimensionIndex();
            else if (axis instanceof SpaceAxisZEntity) idx = ((SpaceAxisZEntity) axis).getDimensionIndex();
            else if (axis instanceof TimeAxisEntity) idx = ((TimeAxisEntity) axis).getDimensionIndex();

            if (idx != null) {
                occupiedIndices.add(idx);
            }
        }

        // 2. 从 1 开始寻找第一个没出现的数字作为 Feature 的维度
        int candidateIndex = 1;
        while (true) {
            if (!occupiedIndices.contains(candidateIndex)) {
                return candidateIndex;
            }
            candidateIndex++;
        }
    }

    private void saveFeatureRelation(FeatureEntity feature, Integer paramId, int layerIndex, Integer dimIndex, LocalDateTime now) {
        Integer featureId = feature.getId();
        // 如果是新特征（没有ID），先入库 Feature 表
        // 注意：这里没有处理重名去重逻辑，如果需要去重请在此处调用 featureDao.selectByCondition 查询
        if (featureId == null) {
            // 简单防重名示例（可选）
            FeatureEntity query = new FeatureEntity();
            query.setFeatureName(feature.getFeatureName());
            List<FeatureEntity> existing = featureDao.selectByCondition(query);
            if (!existing.isEmpty()) {
                featureId = existing.get(0).getId();
            } else {
                feature.setCreatedAt(now);
                feature.setUpdatedAt(now);
                featureDao.insert(feature);
                featureId = feature.getId();
            }
        }

        FeatureParameterEntity relation = new FeatureParameterEntity();
        relation.setParameterId(paramId);
        relation.setFeatureId(featureId);
        relation.setFeatureLayer(layerIndex);

        // 🔥 赋值计算出来的维度索引
        relation.setDimensionIndex(dimIndex);

        featureParameterDao.insert(relation);
    }

//    private Geometry calculateCoverageFromEntities(Geometry originGeo, List<Axis> axes) {
//        // 1. 基础校验
//        if (originGeo == null || !(originGeo instanceof Point)) {
//            return originGeo;
//        }
//        Point origin = (Point) originGeo;
//        Coordinate c = origin.getCoordinate();
//
//        // 2. 解析轴信息
//        Integer xCount = null; Double xRes = null;
//        Integer yCount = null; Double yRes = null;
//        Integer zCount = null; Double zRes = null;
//
//        if (axes != null) {
//            for (Axis axis : axes) {
//                if (axis instanceof SpaceAxisXEntity) {
//                    xCount = ((SpaceAxisXEntity) axis).getCount();
//                    xRes = ((SpaceAxisXEntity) axis).getResolution();
//                } else if (axis instanceof SpaceAxisYEntity) {
//                    yCount = ((SpaceAxisYEntity) axis).getCount();
//                    yRes = ((SpaceAxisYEntity) axis).getResolution();
//                } else if (axis instanceof SpaceAxisZEntity) {
//                    zCount = ((SpaceAxisZEntity) axis).getCount();
//                    zRes = ((SpaceAxisZEntity) axis).getResolution();
//                }
//            }
//        }
//
//        // 如果维度信息缺失，直接返回原点
//        if (xCount == null || yCount == null || xRes == null || yRes == null) {
//            return origin;
//        }
//
//        // 3. 计算 2D 边界 (原点是左上角 minX, maxY)
//        double minX = c.getX();
//        double maxX = minX + (xCount * xRes); // 向东(右)加
//
//        double maxY = c.getY();
//        double minY = maxY - (yCount * yRes); // 向南(下)减 🔥 核心修正
//
//        // 4. 计算 3D 边界 (原点是最高层 maxZ)
//        boolean is3D = (zCount != null && zRes != null && !Double.isNaN(c.getZ()));
//        double maxZ = is3D ? c.getZ() : 0.0;
//        // 如果原点是最高层，那么底层 minZ 应该是 maxZ 减去深度 🔥 核心修正
//        double minZ = is3D ? (maxZ - (zCount * zRes)) : 0.0;
//
//        // 5. 构建多边形 (逆时针顺序：左下 -> 右下 -> 右上 -> 左上 -> 左下)
//        // 注意：为了兼容 PostGIS 等数据库，建议返回 planar (平面) 的多边形。
//        // 如果需要保留 Z 信息，通常建议将 Z 设为 maxZ (顶层平面) 或 minZ (底层平面)，而不是让 Z 乱跳。
//        // 这里我们构建一个位于 "maxZ" 高度的平面多边形，或者纯 2D 多边形。
//
//        Coordinate[] coords;
//        if (is3D) {
//            // 构建 3D 坐标，Z 统一使用 maxZ (或者使用 minZ)，保证多边形是平面的。
//            // 如果 Z 值在四个角不同，JTS 会认为这是一个非法多边形（非平面）。
//            coords = new Coordinate[]{
//                    new Coordinate(minX, minY, maxZ), // 左下
//                    new Coordinate(maxX, minY, maxZ), // 右下
//                    new Coordinate(maxX, maxY, maxZ), // 右上
//                    new Coordinate(minX, maxY, maxZ), // 左上
//                    new Coordinate(minX, minY, maxZ)  // 闭合
//            };
//        } else {
//            // 纯 2D
//            coords = new Coordinate[]{
//                    new Coordinate(minX, minY), // 左下
//                    new Coordinate(maxX, minY), // 右下
//                    new Coordinate(maxX, maxY), // 右上
//                    new Coordinate(minX, maxY), // 左上
//                    new Coordinate(minX, minY)  // 闭合
//            };
//        }
//
//        Polygon p = gf.createPolygon(coords);
//        p.setSRID(4326);
//        return p;
//    }
    /**
     * 🌟 私有工具方法：仅使用 Entity 数据进行几何计算
     * 🚀 严谨版：基于 GeoTools GeodeticCalculator 的 WGS84 椭球体精确偏移推算
     */
    private Geometry calculateCoverageFromEntities(Geometry originGeo, List<Axis> axes) {
        // 1. 基础校验
        if (originGeo == null || !(originGeo instanceof Point)) {
            return originGeo;
        }
        Point origin = (Point) originGeo;
        Coordinate c = origin.getCoordinate();

        // 2. 解析轴信息
        Integer xCount = null; Double xRes = null; String xUnit = null;
        Integer yCount = null; Double yRes = null; String yUnit = null;
        Integer zCount = null; Double zRes = null;

        if (axes != null) {
            for (Axis axis : axes) {
                if (axis instanceof SpaceAxisXEntity) {
                    xCount = ((SpaceAxisXEntity) axis).getCount();
                    xRes = ((SpaceAxisXEntity) axis).getResolution();
                    xUnit = ((SpaceAxisXEntity) axis).getUnit();
                } else if (axis instanceof SpaceAxisYEntity) {
                    yCount = ((SpaceAxisYEntity) axis).getCount();
                    yRes = ((SpaceAxisYEntity) axis).getResolution();
                    yUnit = ((SpaceAxisYEntity) axis).getUnit();
                } else if (axis instanceof SpaceAxisZEntity) {
                    zCount = ((SpaceAxisZEntity) axis).getCount();
                    zRes = ((SpaceAxisZEntity) axis).getResolution();
                }
            }
        }

        if (xCount == null || yCount == null || xRes == null || yRes == null) {
            return origin;
        }

        double minX = c.getX();
        double minY = c.getY();
        double maxX = minX;
        double maxY = minY;

        // =========================================================
        // 🚀 核心严谨计算：使用 GeoTools 测地线计算器 (GeodeticCalculator)
        // =========================================================

        // 3. 计算 X 轴极大值 (向东偏移)
        if ("meter".equalsIgnoreCase(xUnit) || "m".equalsIgnoreCase(xUnit)) {
            GeodeticCalculator calcX = new GeodeticCalculator();
            calcX.setStartingGeographicPoint(minX, minY);
            // 方位角 90.0 度代表正东，距离为米
            calcX.setDirection(90.0, xCount * xRes);
            Point2D destX = calcX.getDestinationGeographicPoint();
            maxX = destX.getX();
        } else {
            // 如果是度，直接相加
            maxX = minX + (xCount * xRes);
        }

        // 4. 计算 Y 轴极大值 (向南偏移)
        if ("meter".equalsIgnoreCase(yUnit) || "m".equalsIgnoreCase(yUnit)) {
            GeodeticCalculator calcY = new GeodeticCalculator();
            calcY.setStartingGeographicPoint(minX, minY);
            // 依据你原本的逻辑：Y轴是向南(下)减的。
            // 方位角 180.0 度代表正南，距离为米
            calcY.setDirection(180.0, yCount * yRes);
            Point2D destY = calcY.getDestinationGeographicPoint();
            maxY = destY.getY();
        } else {
            // 如果是度，依据原逻辑相减
            maxY = minY - (yCount * yRes);
        }

        // 5. 计算 3D 边界与构建多边形 (后续逻辑保持不变)
        boolean is3D = (zCount != null && zRes != null && !Double.isNaN(c.getZ()));
        double maxZ = is3D ? c.getZ() : 0.0;
        double minZ = is3D ? (maxZ - (zCount * zRes)) : 0.0;

        Coordinate[] coords;
        if (is3D) {
            coords = new Coordinate[]{
                    new Coordinate(minX, minY, maxZ),
                    new Coordinate(maxX, minY, maxZ),
                    new Coordinate(maxX, maxY, maxZ),
                    new Coordinate(minX, maxY, maxZ),
                    new Coordinate(minX, minY, maxZ)
            };
        } else {
            coords = new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(maxX, minY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(minX, maxY),
                    new Coordinate(minX, minY)
            };
        }

        Polygon p = gf.createPolygon(coords);
        p.setSRID(4326);
        return p;
    }
    private void saveAxis(Axis axis, Integer paramId, LocalDateTime now) {
        if (axis instanceof SpaceAxisXEntity) {
            SpaceAxisXEntity e = (SpaceAxisXEntity) axis;
            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
            xAxisDao.insert(e);
        } else if (axis instanceof SpaceAxisYEntity) {
            SpaceAxisYEntity e = (SpaceAxisYEntity) axis;
            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
            yAxisDao.insert(e);
        } else if (axis instanceof SpaceAxisZEntity) {
            SpaceAxisZEntity e = (SpaceAxisZEntity) axis;
            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
            zAxisDao.insert(e);
        } else if (axis instanceof TimeAxisEntity) {
            TimeAxisEntity e = (TimeAxisEntity) axis;
            e.setId(null); e.setParameterId(paramId); e.setCreatedAt(now); e.setUpdatedAt(now);
            timeAxisDao.insert(e);
        }
    }
}