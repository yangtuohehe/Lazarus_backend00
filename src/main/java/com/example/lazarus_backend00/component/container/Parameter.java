package com.example.lazarus_backend00.component.container;

import com.example.lazarus_backend00.domain.axis.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

/**
 * 参数业务模型 (充血模型)
 * 在构造时自动根据原点和 X/Y/Z 轴推演 0D/2D/3D 空间覆盖范围。
 */
public class Parameter {

    // ================== 基本属性 ==================

    private final String ioType;
    private final Integer tensorOrder;//说明这个参数属于第几个张量
    private final Point originPoint;

    // 只读属性，由内部计算得出，支持 Point, Polygon(2D), Polygon(3D)
    private final Geometry coverageGeom;
    @JsonProperty("axis")
    private final List<Axis> axisList;
    private final List<Feature> featureList;


    // ================== 构造函数 ==================

    public Parameter(
            String ioType,
            Integer tensorOrder,
            Point originPoint,
            List<Axis> axisList,
            List<Feature> featureList
    ) {
        this.ioType = ioType;
        this.tensorOrder = tensorOrder;
        this.originPoint = originPoint;
        this.axisList = axisList;
        this.featureList = featureList;

        // 触发自计算逻辑
        this.coverageGeom = calculateCoverage(originPoint, axisList);
    }

    public Parameter(
            String ioType,
            Integer tensorOrder,
            Point originPoint,
            Geometry coverageGeom, // <--- 🔥 直接接收数据库的值
            List<Axis> axisList,
            List<Feature> featureList
    ) {
        this.ioType = ioType;
        this.tensorOrder = tensorOrder;
        this.originPoint = originPoint;

        // 🔥 核心区别：直接赋值，不进行 calculateCoverage 计算
        this.coverageGeom = coverageGeom;

        this.axisList = axisList;
        this.featureList = featureList;
    }

    // ================== 内部私有计算逻辑 ==================
    // 🌟 静态 JTS 几何工厂 (全局复用，节省内存)
    private static final GeometryFactory gf = new GeometryFactory();
    /**
     * 核心：根据轴的存在情况，动态计算 0D, 2D 或 3D 几何范围
     */
    private Geometry calculateCoverage(Point origin, List<Axis> axes) {
        if (origin == null) return null;

        SpaceAxisX xAxis = null;
        SpaceAxisY yAxis = null;
        SpaceAxisZ zAxis = null;

        // 1. 动态提取所有空间轴
        if (axes != null) {
            for (Axis axis : axes) {
                if (axis instanceof SpaceAxisX) xAxis = (SpaceAxisX) axis;
                if (axis instanceof SpaceAxisY) yAxis = (SpaceAxisY) axis;
                if (axis instanceof SpaceAxisZ) zAxis = (SpaceAxisZ) axis;
            }
        }

        // =========================================================
        // 【0D 模式】：没有 XY 轴，退化为原点本身 (Point)
        // =========================================================
        if (xAxis == null || yAxis == null) {
            return origin;
        }

        // 提取原点坐标 (X, Y)
        Coordinate originCoord = origin.getCoordinate();
        double minX = originCoord.getX();
        double minY = originCoord.getY();

        // 🌟 安全提取 Z 轴起点 (处理原点只有 2D 的情况)
        double minZ = Double.isNaN(originCoord.getZ()) ? 0.0 : originCoord.getZ();

        // 计算 XY 最大极值
        double maxX = minX + (xAxis.getCount() * xAxis.getResolution());
        double maxY = minY + (yAxis.getCount() * yAxis.getResolution());

        // =========================================================
        // 【3D 模式】：若存在 Z 轴，创建三维多边形 (Polygon Z)
        // =========================================================
        if (zAxis != null) {
            double maxZ = minZ + (zAxis.getCount() * zAxis.getResolution());

            // 构造带 Z 值的坐标点
            Coordinate[] coords3D = new Coordinate[]{
                    new Coordinate(minX, minY, minZ),
                    new Coordinate(minX, maxY, maxZ), // 利用高位点撑起 3D 包围盒
                    new Coordinate(maxX, maxY, maxZ),
                    new Coordinate(maxX, minY, minZ),
                    new Coordinate(minX, minY, minZ)  // 闭合
            };
            Polygon poly3D = gf.createPolygon(coords3D);
            poly3D.setSRID(4326);
            return poly3D;
        }
        // =========================================================
        // 【2D 模式】：常规二维多边形 (Polygon)
        // =========================================================
        else {
            Coordinate[] coords2D = new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(minX, maxY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(maxX, minY),
                    new Coordinate(minX, minY)
            };
            Polygon poly2D = gf.createPolygon(coords2D);
            poly2D.setSRID(4326);
            return poly2D;
        }
    }

    // ================== Getter 方法 ==================
    public String getIoType() { return ioType; }
    public Integer getTensorOrder() { return tensorOrder; }
    public Point getOriginPoint() { return originPoint; }
    public Geometry getCoverageGeom() { return coverageGeom; }
    public List<Axis> getAxisList() { return axisList; }
    public List<Feature> getFeatureList() { return featureList; }

    public TimeAxis getTimeAxis() {
        if (axisList != null) {
            for (Axis axis : axisList) {
                if (axis instanceof TimeAxis) {
                    return (TimeAxis) axis;
                }
            }
        }
        return null;
    }
}