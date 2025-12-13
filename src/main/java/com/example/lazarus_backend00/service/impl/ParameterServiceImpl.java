package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.ParameterDao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;
import com.example.lazarus_backend00.service.ParameterService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class ParameterServiceImpl implements ParameterService {

    private final ParameterDao parameterDao;

    public ParameterServiceImpl(ParameterDao parameterDao) {
        this.parameterDao = parameterDao;
    }

    @Override
    public Integer createParameter(ParameterEntity entity) {
        // 强制忽略传入的主键
        entity.setParameterId(null);

        // 生成创建 / 更新时间
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        // 调用 DAO 入库，coverage_geom 在 SQL 中自动生成
        parameterDao.insertParameter(entity);

        // 入库后 coverageGeom 会自动写回对象
        // 注意：如果你的 Mapper 没有返回 coverageGeom，这里需要手动再查询一次
        // 下面示例假设 Mapper 会返回 coverage_geom 并写回对象（PostgreSQL RETURNING）
        // entity.getCoverageGeom() 已包含入库后的 WKB

        return entity.getParameterId();
    }

}