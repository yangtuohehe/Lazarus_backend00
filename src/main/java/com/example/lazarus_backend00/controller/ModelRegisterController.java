package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.dto.*;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.service.ModelRegisterService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/model")
@CrossOrigin(origins = "http://localhost:5173")
public class ModelRegisterController {

    private final ModelRegisterService modelRegisterService;

    // 指定 SRID=4326 (WGS84)
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    public ModelRegisterController(ModelRegisterService modelRegisterService) {
        this.modelRegisterService = modelRegisterService;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registerModel(
            @RequestPart("payload") ModelRegisterRequest requestDto,
            @RequestPart("modelFile") MultipartFile modelFile
    ) throws Exception {

        System.out.println("===== 接收到请求: 开始解析 =====");

        // 1. Model
        DynamicProcessModelEntity modelEntity = new DynamicProcessModelEntity();
        // ✅ 修正：使用标准的驼峰命名 Getter (Lombok 生成)
        BeanUtils.copyProperties(requestDto.getDynamicProcessModel(), modelEntity);
        modelEntity.setModelFile(modelFile.getBytes());

        // 2. Interface
        ModelInterfaceEntity interfaceEntity = new ModelInterfaceEntity();
        // ✅ 修正：使用标准的驼峰命名 Getter
        BeanUtils.copyProperties(requestDto.getModelInterface(), interfaceEntity);
        interfaceEntity.setProcessmodelId(null);

        // 3. Parameters
        List<ParameterEntity> parameterEntities = new ArrayList<>();
        List<List<FeatureEntity>> featureMatrix = new ArrayList<>();
        List<List<Axis>> axisMatrix = new ArrayList<>();

        // ✅ 修正：使用 getModelInterface()
        if (requestDto.getModelInterface().getParameters() != null) {
            for (ParameterDTO pDto : requestDto.getModelInterface().getParameters()) {

                ParameterEntity param = new ParameterEntity();
                param.setIoType(pDto.getIoType());
                param.setTensorOrder(pDto.getTensorOrder());

                // 处理原点 (2D/3D)
                Coordinate coord;
                if (pDto.getOriginPointAlt() != null) {
                    coord = new Coordinate(pDto.getOriginPointLon(), pDto.getOriginPointLat(), pDto.getOriginPointAlt());
                } else {
                    coord = new Coordinate(pDto.getOriginPointLon(), pDto.getOriginPointLat());
                }
                param.setOriginPoint(gf.createPoint(coord));

                parameterEntities.add(param);

                // Axis 列表处理
                List<Axis> currentAxisList = new ArrayList<>();
                if (pDto.getAxis() != null) {
                    for (AxisDTO aDto : pDto.getAxis()) {
                        // 调用下方的转换方法
                        currentAxisList.add(convertAxisDtoToEntity(aDto));
                    }
                }
                axisMatrix.add(currentAxisList);

                // Feature 列表处理
                List<FeatureEntity> currentFeatureList = new ArrayList<>();
                if (pDto.getFeatures() != null) {
                    for (FeatureDTO fDto : pDto.getFeatures()) {
                        FeatureEntity feature = new FeatureEntity();
                        feature.setFeatureName(fDto.getFeatureName());
                        currentFeatureList.add(feature);
                    }
                }
                featureMatrix.add(currentFeatureList);
            }
        }

        Integer processmodelId = modelRegisterService.registerModel(
                modelEntity, interfaceEntity, parameterEntities, featureMatrix, axisMatrix
        );

        return ResponseEntity.ok("成功入库, 模型ID: " + processmodelId);
    }


    /**
     * 【调试专用接口】
     * 作用：生成一个后端完美兼容的 DTO 对象，并以 JSON 返回。
     * 用法：调用此接口，复制返回的 JSON，那就是标准答案。
     */
    @GetMapping("/structure")
    public ResponseEntity<ModelRegisterRequest> getStandardJsonStructure() {
        ModelRegisterRequest request = new ModelRegisterRequest();

        // 1. 组装 Model 部分 (重点关注 version 类型)
        DynamicProcessModelDTO modelDto = new DynamicProcessModelDTO();
        modelDto.setModelName("标准结构示例模型");
        modelDto.setModelAuthor("DebugTool");
        modelDto.setModelSummary("此 JSON 由后端直接生成，格式绝对正确");
        modelDto.setModelSourcePaper("无");
        modelDto.setVersion(1); // ✅ 重点：设置整数 1
        request.setDynamicProcessModel(modelDto); // 这里的 Setter 决定了 JSON Key 是 dynamicProcessModel

        // 2. 组装 Interface 部分
        ModelInterfaceDTO interfaceDto = new ModelInterfaceDTO();
        interfaceDto.setInterfaceName("标准接口");
        interfaceDto.setDefault(true);
        interfaceDto.setInterfaceSummary("调试用");

        // 3. 组装 Parameter 部分 (重点关注 tensorOrder 类型)
        List<ParameterDTO> params = new ArrayList<>();
        ParameterDTO param = new ParameterDTO();
        param.setIoType("INPUT");
        param.setTensorOrder(0); // ✅ 重点：设置整数 0
        param.setOriginPointLon(110.5);
        param.setOriginPointLat(20.0);

        // 4. 组装 Axis 部分 (重点关注 type 字段)
        List<AxisDTO> axes = new ArrayList<>();

        // 时间轴
        TimeAxisDTO tAxis = new TimeAxisDTO();
        tAxis.setType("TIME"); // ✅ 重点：后端靠这个字符串区分类型
        tAxis.setDimensionIndex(1);
        tAxis.setCount(10);
        tAxis.setResolution(1.0);
        tAxis.setUnit("hour");
        axes.add(tAxis);

        // 空间轴
        SpaceAxisXDTO xAxis = new SpaceAxisXDTO();
        xAxis.setType("SPACE_X"); // ✅ 重点
        xAxis.setDimensionIndex(2);
        xAxis.setCount(50);
        xAxis.setResolution(0.1);
        xAxis.setUnit("degree");
        axes.add(xAxis);

        param.setAxis(axes);

        // 5. 组装 Feature 部分
        List<FeatureDTO> features = new ArrayList<>();
        FeatureDTO f = new FeatureDTO();
        f.setFeatureName("temperature");
        features.add(f);

        param.setFeatures(features);

        params.add(param);
        interfaceDto.setParameters(params);

        request.setModelInterface(interfaceDto); // 这里的 Setter 决定了 JSON Key 是 modelInterface

        return ResponseEntity.ok(request);
    }






    private Axis convertAxisDtoToEntity(AxisDTO dto) {
        Axis entity;
        if (dto instanceof TimeAxisDTO) entity = new TimeAxisEntity();
        else if (dto instanceof SpaceAxisXDTO) entity = new SpaceAxisXEntity();
        else if (dto instanceof SpaceAxisYDTO) entity = new SpaceAxisYEntity();
        else if (dto instanceof SpaceAxisZDTO) entity = new SpaceAxisZEntity();
        else throw new IllegalArgumentException("Unknown Axis Type");
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}