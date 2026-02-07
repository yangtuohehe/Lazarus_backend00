package com.example.lazarus_backend00.infrastructure.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(Geometry.class)
public class JtsGeometryTypeHandler extends BaseTypeHandler<Geometry> {

    // 使用 WKB (二进制) 传输数据，比 WKT (文本) 性能更好，也不会有乱码问题
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final WKBReader wkbReader = new WKBReader(geometryFactory);
    private final WKBWriter wkbWriter = new WKBWriter(2, true); // 2D, 带 SRID

    // =====================================================================
    // 写入数据库：Java Geometry -> PostGIS (WKB byte[])
    // =====================================================================
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Geometry parameter, JdbcType jdbcType) throws SQLException {
        // 强制使用 SRID 4326 (WGS84 经纬度)
        if (parameter.getSRID() == 0) {
            parameter.setSRID(4326);
        }
        // 将 Geometry 序列化为 WKB 字节流写入数据库
        ps.setBytes(i, wkbWriter.write(parameter));
    }

    // =====================================================================
    // 读取数据库：PostGIS (WKB byte[]) -> Java Geometry
    // =====================================================================
    @Override
    public Geometry getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toGeometry(rs.getBytes(columnName));
    }

    @Override
    public Geometry getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toGeometry(rs.getBytes(columnIndex));
    }

    @Override
    public Geometry getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toGeometry(cs.getBytes(columnIndex));
    }

    // 解析 WKB 的辅助方法
    private Geometry toGeometry(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return wkbReader.read(bytes);
        } catch (Exception e) {
            throw new RuntimeException("空间数据(WKB)解析失败", e);
        }
    }
}
