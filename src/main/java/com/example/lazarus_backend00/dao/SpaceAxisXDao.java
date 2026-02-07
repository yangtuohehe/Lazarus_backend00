package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.SpaceAxisXEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * SpaceAxisXMapper
 *
 * 空间 X 轴数据访问接口
 * 负责 space_axis_x 表的增查操作
 */
@Mapper
public interface SpaceAxisXDao {

    /**
     * 插入一条 SpaceAxisX 记录
     */
    int insert(SpaceAxisXEntity entity);

    /**
     * 根据 ID 查询 SpaceAxisX
     */
    SpaceAxisXEntity selectById(Integer id);

    /**
     * 根据 parameterId 查询 SpaceAxisX (新增，对应 XML)
     */
    SpaceAxisXEntity selectByParameterId(Integer parameterId);

    /**
     * 根据非空字段动态查询 SpaceAxisX
     */
    List<SpaceAxisXEntity> selectByCondition(SpaceAxisXEntity entity);
}