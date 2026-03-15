package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import com.example.lazarus_backend00.service.ContainerPoolService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/container")
@CrossOrigin(origins = "*") // 允许前端跨域
public class ContainerPoolController {

    private final ContainerPoolService containerPoolService;
    // 2. 使用构造器注入 (Spring 4.3+ 自动识别，无需 @Autowired)
    public ContainerPoolController(ContainerPoolService containerPoolService) {
        this.containerPoolService = containerPoolService;
    }
    /**
     * 1. 获取模型库列表 (左侧侧边栏)
     * 查询数据库中所有可用的模型
     */
    @GetMapping("/models")
    public List<ModelContainerDTO> getModelLibrary() {
        return containerPoolService.getAllModels();
    }

    /**
     * 2. 注册并实例化容器 (右侧容器池 + 地图绘图)
     * 前端传入 modelId，后端实例化容器，并返回包含空间范围(Geometry)的详细 DTO
     */
//    @PostMapping("/register")
//    public ModelContainerDTO registerContainer(@RequestParam("modelId") Integer modelId) {
//        return containerPoolService.registerContainer(modelId);
//    }
// 修改后：
    @PostMapping("/register")
    public ModelContainerDTO registerContainer(
            @RequestParam("modelId") Integer modelId,
            // 新增：允许前端传入具体的参数接口 ID，不传则默认为 null
            @RequestParam(value = "interfaceId", required = false) Integer interfaceId) {
        // 将 interfaceId 透传给 Service
        return containerPoolService.registerContainer(modelId, interfaceId);
    }
    /**
     * 3. 获取当前容器池状态 (用于刷新列表)
     */
    @GetMapping("/pool")
    public List<ModelContainerDTO> getPoolStatus() {
        return containerPoolService.getRunningContainers();
    }

    @GetMapping("/models/{modelId}/interfaces")
    public List<ModelInterfaceEntity> getModelInterfaces(@PathVariable Integer modelId) {
        return containerPoolService.getModelInterfaces(modelId);
    }

    @GetMapping("/interfaces/{interfaceId}/parameters")
    public List<Parameter> getInterfaceParameters(@PathVariable Integer interfaceId) {
        return containerPoolService.getInterfaceParameters(interfaceId);
    }
}