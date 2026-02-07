package com.example.lazarus_backend00.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import org.locationtech.jts.geom.Geometry;
import com.example.lazarus_backend00.component.container.ContainerStatus;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 容器实例传输对象
 * 包含：原子状态控制、模型元数据、空间范围参数
 */
public class ModelContainerDTO implements Serializable {

    // ==========================================
    // 2. 核心字段
    // ==========================================

    // 🔥 修改点：类型确认是 Integer
    private Integer id;

    // 线程安全的状态 (按要求使用 AtomicReference)
    private final AtomicReference<ContainerStatus> status;

    private String modelName;
    private String modelAuthor;
    private Integer version;

    // 自定义的参数列表 (包含空间范围)
    private List<ContainerParameter> parameters;

    // ==========================================
    // 3. 构造函数 (No Lombok)
    // ==========================================

    public ModelContainerDTO() {
        // 默认初始化状态为 CREATED
        this.status = new AtomicReference<>(ContainerStatus.CREATED);
    }

    // 🔥 修改点：构造函数参数 id 改为 Integer
    public ModelContainerDTO(Integer id, String modelName, String modelAuthor, Integer version, List<ContainerParameter> parameters) {
        this.id = id;
        this.status = new AtomicReference<>(ContainerStatus.CREATED);
        this.modelName = modelName;
        this.modelAuthor = modelAuthor;
        this.version = version;
        this.parameters = parameters;
    }

    // ==========================================
    // 4. Getters & Setters (No Lombok)
    // ==========================================

    // 🔥 修改点：返回类型改为 Integer
    public Integer getId() {
        return id;
    }

    // 🔥 修改点：参数类型改为 Integer
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * 获取状态值
     * 注意：JSON 序列化时会调用此方法，返回具体的枚举字符串 (如 "RUNNING")
     */
    public ContainerStatus getStatus() {
        return status.get();
    }

    /**
     * 设置状态值 (线程安全更新)
     */
    public void setStatus(ContainerStatus newStatus) {
        this.status.set(newStatus);
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelAuthor() {
        return modelAuthor;
    }

    public void setModelAuthor(String modelAuthor) {
        this.modelAuthor = modelAuthor;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<ContainerParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ContainerParameter> parameters) {
        this.parameters = parameters;
    }

    // ==========================================
    // 5. 内部类：参数定义 (包含空间信息)
    // ==========================================
    public static class ContainerParameter implements Serializable {

        private String ioType;          // INPUT 或 OUTPUT
        private Geometry coverageGeom;  // PostGIS 几何对象
        private List<String> featureNames; // 特征名称列表

        public ContainerParameter() {}

        public ContainerParameter(String ioType, Geometry coverageGeom, List<String> featureNames) {
            this.ioType = ioType;
            this.coverageGeom = coverageGeom;
            this.featureNames = featureNames;
        }

        public String getIoType() {
            return ioType;
        }

        public void setIoType(String ioType) {
            this.ioType = ioType;
        }

        public Geometry getCoverageGeom() {
            return coverageGeom;
        }

        public void setCoverageGeom(Geometry coverageGeom) {
            this.coverageGeom = coverageGeom;
        }

        public List<String> getFeatureNames() {
            return featureNames;
        }

        public void setFeatureNames(List<String> featureNames) {
            this.featureNames = featureNames;
        }
    }
}