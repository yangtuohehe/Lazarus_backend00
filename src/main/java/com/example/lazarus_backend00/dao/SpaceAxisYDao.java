package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.SpaceAxisYEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * SpaceAxisYMapper
 * 空间 Y 轴数据访问接口
 */
@Mapper
public interface SpaceAxisYDao {

    /**
     * 插入
     */
    int insert(SpaceAxisYEntity entity);

    /**
     * 根据 ID 查询
     */
    SpaceAxisYEntity selectById(Integer id);

    /**
     * 根据 parameterId 查询 (新增，对应 XML)
     */
    SpaceAxisYEntity selectByParameterId(Integer parameterId);

    /**
     * 动态查询
     */
    List<SpaceAxisYEntity> selectByCondition(SpaceAxisYEntity entity);
}