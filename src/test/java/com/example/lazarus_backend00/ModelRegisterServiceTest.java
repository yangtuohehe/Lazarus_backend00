package com.example.lazarus_backend00;

import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.service.ModelRegisterService;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional // 如果想在数据库看结果请注释掉，如果想自动回滚保持环境干净请打开
class ModelRegisterServiceTest {

    @Autowired
    private ModelRegisterService modelRegisterService;

    @Test
    void testRegisterModelWithComplexTensors() throws ParseException {
        System.out.println("===== 开始模型入库测试 (Service层集成测试) =====");

        // 使用 SRID 4326 (WGS84)
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        WKTReader wktReader = new WKTReader(gf);

        // =====================================================================
        // 1. 准备：模型 (DynamicProcessModel)
        // =====================================================================
        DynamicProcessModelEntity processModel = new DynamicProcessModelEntity();
        processModel.setModelName("全局气象预测模型 (Service测试)");
        processModel.setModelAuthor("Lazarus Team");
        processModel.setModelSourcePaper("Nature Geoscience, 2026");
        processModel.setModelFile(new byte[]{1, 2, 3});
        processModel.setVersion(1);

        // =====================================================================
        // 2. 准备：接口 (ModelInterface)
        // =====================================================================
        ModelInterfaceEntity modelInterface = new ModelInterfaceEntity();
        modelInterface.setInterfaceName("Default Run");
        modelInterface.setIsDefault(true);

        // =====================================================================
        // 3. 准备：参数列表 (Parameter)
        // =====================================================================
        List<ParameterEntity> parameters = new ArrayList<>();

        // --- 参数 1 (索引 0) ---
        ParameterEntity param1 = new ParameterEntity();
        param1.setIoType("INPUT");
        // ✅ 修改点：tensorOrder 改为 Integer，表示这是第 0 个参数
        param1.setTensorOrder(0);
        param1.setOriginPoint(wktReader.read("POINT(110.0 20.0)"));
        // 注意：不设置 CoverageGeom，验证 Service 是否会自动计算
        parameters.add(param1);

        // --- 参数 2 (索引 1) ---
        ParameterEntity param2 = new ParameterEntity();
        param2.setIoType("OUTPUT");
        // ✅ 修改点：tensorOrder 改为 Integer，表示这是第 1 个参数
        param2.setTensorOrder(1);
        param2.setOriginPoint(wktReader.read("POINT(110.0 20.0)"));
        parameters.add(param2);


        // =====================================================================
        // 4. 准备：轴矩阵 (List<List<Axis>>)
        // =====================================================================
        List<List<Axis>> axesMatrix = new ArrayList<>();

        // 4.1 参数 1 的轴：Time, X, Y
        TimeAxisEntity tAxis = new TimeAxisEntity();
        tAxis.setDimensionIndex(0); tAxis.setCount(24); tAxis.setResolution(1.0); tAxis.setUnit("hour");

        SpaceAxisXEntity xAxis1 = new SpaceAxisXEntity();
        xAxis1.setDimensionIndex(1); xAxis1.setCount(100); xAxis1.setResolution(0.1); xAxis1.setUnit("degree");

        SpaceAxisYEntity yAxis1 = new SpaceAxisYEntity();
        yAxis1.setDimensionIndex(2); yAxis1.setCount(100); yAxis1.setResolution(0.1); yAxis1.setUnit("degree");

        axesMatrix.add(Arrays.asList(tAxis, xAxis1, yAxis1));

        // 4.2 参数 2 的轴：X, Y
        SpaceAxisXEntity xAxis2 = new SpaceAxisXEntity();
        xAxis2.setDimensionIndex(0); xAxis2.setCount(50); xAxis2.setResolution(0.2); xAxis2.setUnit("degree");

        SpaceAxisYEntity yAxis2 = new SpaceAxisYEntity();
        yAxis2.setDimensionIndex(1); yAxis2.setCount(50); yAxis2.setResolution(0.2); yAxis2.setUnit("degree");

        axesMatrix.add(Arrays.asList(xAxis2, yAxis2));


        // =====================================================================
        // 5. 准备：特征矩阵 (List<List<Feature>>)
        // =====================================================================
        List<List<FeatureEntity>> featuresMatrix = new ArrayList<>();

        // 5.1 参数 1 的特征
        FeatureEntity f1 = new FeatureEntity(); f1.setFeatureName("温度");
        FeatureEntity f2 = new FeatureEntity(); f2.setFeatureName("湿度");
        featuresMatrix.add(Arrays.asList(f1, f2));

        // 5.2 参数 2 的特征
        FeatureEntity f3 = new FeatureEntity(); f3.setFeatureName("降水");
        featuresMatrix.add(List.of(f3));


        // =====================================================================
        // 6. 执行测试
        // =====================================================================
        Integer newModelId = modelRegisterService.registerModel(
                processModel,
                modelInterface,
                parameters,
                featuresMatrix,
                axesMatrix
        );

        // =====================================================================
        // 7. 验证结果
        // =====================================================================
        System.out.println("✔ 模型成功入库，生成的主键 ID: " + newModelId);

        // 7.1 基础断言
        assertNotNull(newModelId, "模型ID不应为空");
        assertTrue(newModelId > 0, "模型ID应为正整数");

        // 7.2 验证 TensorOrder
        // 注意：parameters 列表里的对象会被 Hibernate/MyBatis 回填 ID，
        // 我们可以检查它们是否被修改，或者重新查询数据库。
        // 这里假设 Service 是引用传递处理，直接检查对象
        System.out.println("Param1 TensorOrder: " + parameters.get(0).getTensorOrder());
        System.out.println("Param2 TensorOrder: " + parameters.get(1).getTensorOrder());

        // 7.3 ✅ 核心验证：验证 Service 是否自动计算了 CoverageGeom
        assertNotNull(parameters.get(0).getCoverageGeom(), "Service 应该自动计算 Param1 的覆盖范围");
        assertNotNull(parameters.get(1).getCoverageGeom(), "Service 应该自动计算 Param2 的覆盖范围");

        System.out.println("✔ Param1 自动生成的覆盖范围: " + parameters.get(0).getCoverageGeom());
        System.out.println("✔ Param2 自动生成的覆盖范围: " + parameters.get(1).getCoverageGeom());

        System.out.println("===== 测试通过 =====");
    }
}