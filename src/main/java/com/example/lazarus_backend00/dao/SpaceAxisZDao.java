package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.SpaceAxisZEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SpaceAxisZMapper
 *
 * 空间 Z 轴数据访问接口
 * 负责 space_axis_z 表的增查操作
 */
@Mapper
public interface SpaceAxisZDao {

    /**
     * 插入一条 SpaceAxisZ 记录
     *
     * @param entity SpaceAxisZEntity，包含轴范围和分辨率信息
     * @return 受影响行数（通常为 1）
     */
    int insert(SpaceAxisZEntity entity);

    /**
     * 根据 ID 查询 SpaceAxisZ
     *
     * @param id 主键 ID
     * @return SpaceAxisZEntity，如果不存在则返回 null
     */
    SpaceAxisZEntity selectById(Integer id);

    /**
     * 根据 parameterId 查询 (对应 XML 中的 selectByParameterId)
     */
    SpaceAxisZEntity selectByParameterId(Integer parameterId);

    /**
     * 根据非空字段动态查询 SpaceAxisZ
     *
     * @param entity 查询条件，非空字段生效
     * @return SpaceAxisZSEntity 列表
     */
    List<SpaceAxisZEntity> selectByCondition(SpaceAxisZEntity entity);
}