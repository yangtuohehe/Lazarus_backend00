package com.example.lazarus_backend00;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;
import com.example.lazarus_backend00.service.ParameterService;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
public class ParameterInsertTest {

    @Autowired
    private ParameterService parameterService;

    @Test
    void testInsertParameter() {

        String ioType = "input";
        Integer temporalResolutionValue = 1;
        String temporalResolutionUnit = "hour";
        Integer temporalRangeValue = 12;
        String temporalRangeUnit = "hour";

        double originPointLon = 115.42520833333334;
        double originPointLat = 9.784791666666665;

        Integer rowCount = 24;
        Integer columnCount = 24;
        Integer zCount = null;

        Double spatialResolutionX = 0.009583333333332908;
        Double spatialResolutionY = 0.009583333333333352;
        Double spatialResolutionZ = 1000.0;
        String spatialResolutionUnit = "度";

        String tensorOrderRaw = "[c,t,h,w]";

        // ================= 创建实体 =================
        ParameterEntity p = new ParameterEntity();

        p.setInterfaceId(8);
        p.setIoType(ioType);
        p.setTemporalResolutionValue(temporalResolutionValue);
        p.setTemporalResolutionUnit(temporalResolutionUnit);
        p.setTemporalRangeValue(temporalRangeValue);
        p.setTemporalRangeUnit(temporalRangeUnit);

        // =========== origin_point → WKT String ===========
        GeometryFactory gf = new GeometryFactory();
        Point point = gf.createPoint(new Coordinate(originPointLon, originPointLat));
        point.setSRID(4326);
        p.setOriginPoint(point.toText());     // 关键：WKT，而不是 WKB

        // =============== 维度和分辨率 ===============
        p.setRowCount(rowCount);
        p.setColumnCount(columnCount);
        p.setZCount(zCount);

        p.setSpatialResolutionX(spatialResolutionX);
        p.setSpatialResolutionY(spatialResolutionY);
        p.setSpatialResolutionZ(spatialResolutionZ);
        p.setSpatialResolutionUnit(spatialResolutionUnit);

        // ========== tensor_order ==========
        p.setTensorOrder(tensorOrderRaw);

        LocalDateTime now = LocalDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);

        // ================= 调用入库 =================
        Integer newId = parameterService.createParameter(p);

        System.out.println("参数插入成功，新 ID = " + newId);
    }
}
