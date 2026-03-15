package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.dto.*;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.service.ModelRegisterService;
import com.example.lazarus_backend00.service.ModelSelectService;
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
    private final ModelSelectService modelSelectService;
    // 指定 SRID=4326 (WGS84)
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    public ModelRegisterController(ModelRegisterService modelRegisterService,
                                   ModelSelectService modelSelectService) {
        this.modelRegisterService = modelRegisterService;
        this.modelSelectService = modelSelectService;
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
                param.setoTimeStep(pDto.getoTimeStep());
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
     * ✅ 获取数据库中真实的模型结构数据
     */
    @GetMapping("/structure")
    public ResponseEntity<?> getRealJsonStructure(@RequestParam(value = "modelId", defaultValue = "2") Integer modelId) {

        // 调用 Service 从数据库获取真实聚合出来的 JSON
        String realJsonData = modelSelectService.selectModelById(modelId);

        if (realJsonData == null || realJsonData.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 返回真实的 JSON 字符串，并指定 Content-Type 为 application/json
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(realJsonData);
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