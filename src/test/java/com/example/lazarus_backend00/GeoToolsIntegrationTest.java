package com.example.lazarus_backend00;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GeoToolsIntegrationTest {

    @Test
    void testGeoToolsCreateCoverageGeom() {

        // =============================
        // 1. 模拟 parameter 表中的字段
        // =============================
        double originX = 120.0;
        double originY = 30.0;

        int rowCount = 100;
        int columnCount = 200;

        double resX = 0.01;
        double resY = 0.01;

        // =============================
        // 2. 计算 Coverage 范围
        // =============================
        double maxX = originX + columnCount * resX;
        double maxY = originY + rowCount * resY;

        // =============================
        // 3. 创建 Polygon (EPSG:4326)
        // =============================
        GeometryFactory geometryFactory = new GeometryFactory(
                new PrecisionModel(),
                4326
        );

        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(originX, originY),
                new Coordinate(maxX, originY),
                new Coordinate(maxX, maxY),
                new Coordinate(originX, maxY),
                new Coordinate(originX, originY) // 闭合
        };

        LinearRing shell = geometryFactory.createLinearRing(coordinates);
        Polygon coveragePolygon = geometryFactory.createPolygon(shell);

        // =============================
        // 4. 转为 WKB（用于 PostGIS）
        // =============================
        WKBWriter writer = new WKBWriter();
        byte[] wkb = writer.write(coveragePolygon);

        // =============================
        // 5. 断言
        // =============================
        assertNotNull(coveragePolygon);
        assertTrue(coveragePolygon.isValid());
        assertNotNull(wkb);
        assertTrue(wkb.length > 0);

        // =============================
        // 6. 控制台输出（调试用）
        // =============================
        System.out.println("Coverage Polygon: " + coveragePolygon);
        System.out.println("WKB length: " + wkb.length);
    }
}
