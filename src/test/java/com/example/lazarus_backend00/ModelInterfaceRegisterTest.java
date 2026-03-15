package com.example.lazarus_backend00;

import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.service.ModelRegisterService;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
public class ModelInterfaceRegisterTest {

    @Autowired
    private ModelRegisterService modelRegisterService;

    // 全局复用的 JTS 几何工厂
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @Transactional       // 开启事务
    @Rollback(value = false)      // 测试完成后强制回滚，不污染数据库
    public void testAddRemainingInterfaces() {
        // ⚠️ 请将这里的 ID 替换为你刚才入库“三岛模型(北岛接口)”后，数据库里生成的那个模型 ID！
        Integer targetModelId = 9;

        System.out.println("🚀 开始为模型 (ID: " + targetModelId + ") 追加剩余接口...");

        // 1. 注入【中岛接口】
        // 中岛 NDVI 参数的特定原点：112.3218396966969, 16.95713829221056
        buildAndRegisterInterface(targetModelId, "中岛接口", "中岛专属接口",
                112.3218396966969, 16.95713829221056);

        // 2. 注入【南岛接口】
        // 南岛 NDVI 参数的特定原点：112.33145167023697, 16.94977210688078
        buildAndRegisterInterface(targetModelId, "南岛接口", "南岛专属接口",
                112.33145167023697, 16.94977210688078);

        System.out.println("✅ 测试完成！(事务将自动回滚)");
    }

    /**
     * 封装的通用接口构建方法
     * 因为中岛和南岛的参数结构完全一致，只有接口名称和 NDVI 张量的原点不同
     */
    private void buildAndRegisterInterface(Integer modelId, String interfaceName, String summary, double ndviLon, double ndviLat) {
        // 1. 初始化接口表
        ModelInterfaceEntity inf = new ModelInterfaceEntity();
        inf.setInterfaceName(interfaceName);
        inf.setInterfaceSummary(summary);
        inf.setIsDefault(false); // 已经有北岛是默认的了

        // 2. 准备参数、特征、轴的容器
        List<ParameterEntity> parameters = new ArrayList<>();
        List<List<FeatureEntity>> features = new ArrayList<>();
        List<List<Axis>> axes = new ArrayList<>();

        // ==========================================================
        // Param 0: NDVI (INPUT) - 坐标根据传入变化
        // ==========================================================
        ParameterEntity p0 = new ParameterEntity();
        p0.setIoType("INPUT");
        p0.setTensorOrder(0);
        p0.setoTimeStep(0);
        p0.setOriginPoint(gf.createPoint(new Coordinate(ndviLon, ndviLat)));
        parameters.add(p0);

        features.add(Arrays.asList(createFeature("ndvi")));

        axes.add(Arrays.asList(
                createTimeAxis(2, 3, 1.0, "month"),
                createSpaceYAxis(3, 100, 10.0, "meter"),
                createSpaceXAxis(4, 130, 10.0, "meter")
        ));

        // ==========================================================
        // Param 1: 气象环境 (INPUT) - 坐标固定 112.31, 16.97
        // ==========================================================
        ParameterEntity p1 = new ParameterEntity();
        p1.setIoType("INPUT");
        p1.setTensorOrder(1);
        p1.setoTimeStep(0);
        p1.setOriginPoint(gf.createPoint(new Coordinate(112.31, 16.97)));
        parameters.add(p1);

        features.add(Arrays.asList(
                createFeature("t2m"),
                createFeature("irradiation"),
                createFeature("rainfall")
        ));

        axes.add(Arrays.asList(
                createTimeAxis(2, 3, 1.0, "month"),
                createSpaceYAxis(3, 1, 0.25, "degree"),
                createSpaceXAxis(4, 1, 0.25, "degree")
        ));

        // ==========================================================
        // Param 2: NDVI (OUTPUT) - 坐标根据传入变化
        // ==========================================================
        ParameterEntity p2 = new ParameterEntity();
        p2.setIoType("OUTPUT");
        p2.setTensorOrder(0);
        p2.setoTimeStep(3);
        p2.setOriginPoint(gf.createPoint(new Coordinate(ndviLon, ndviLat)));
        parameters.add(p2);

        features.add(Arrays.asList(createFeature("ndvi")));

        axes.add(Arrays.asList(
                createTimeAxis(0, 3, 1.0, "month"),
                createSpaceYAxis(1, 100, 10.0, "meter"),
                createSpaceXAxis(2, 130, 10.0, "meter")
        ));

        // ==========================================================
        // 3. 核心调用：执行入库逻辑
        // ==========================================================
        Integer newInterfaceId = modelRegisterService.addInterfaceToModel(modelId, inf, parameters, features, axes);
        System.out.println("   --> 成功模拟注入接口 [" + interfaceName + "]，分配到的 ID: " + newInterfaceId);
    }

    // --------------- 下面是对象构建的工具包 ---------------

    private FeatureEntity createFeature(String name) {
        FeatureEntity f = new FeatureEntity();
        f.setFeatureName(name);
        return f;
    }

    private TimeAxisEntity createTimeAxis(int index, int count, double res, String unit) {
        TimeAxisEntity a = new TimeAxisEntity();
        a.setDimensionIndex(index); a.setCount(count); a.setResolution(res); a.setUnit(unit);
        return a;
    }

    private SpaceAxisYEntity createSpaceYAxis(int index, int count, double res, String unit) {
        SpaceAxisYEntity a = new SpaceAxisYEntity();
        a.setDimensionIndex(index); a.setCount(count); a.setResolution(res); a.setUnit(unit);
        return a;
    }

    private SpaceAxisXEntity createSpaceXAxis(int index, int count, double res, String unit) {
        SpaceAxisXEntity a = new SpaceAxisXEntity();
        a.setDimensionIndex(index); a.setCount(count); a.setResolution(res); a.setUnit(unit);
        return a;
    }
}