package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.TimeAxisEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * TimeAxisMapper
 *
 * 时间 轴数据访问接口
 * 负责 time_axis 表的增查操作
 */
@Mapper
public interface TimeAxisDao {

    /**
     * 插入一条 TimeAxis 记录
     *
     * @param entity TimeAxisEntity，包含轴范围和分辨率信息
     * @return 受影响行数（通常为 1）
     */
    int insert(TimeAxisEntity entity);

    /**
     * 根据 ID 查询 TimeAxis
     *
     * @param id 主键 ID
     * @return TimeAxisEntity，如果不存在则返回 null
     */
    TimeAxisEntity selectById(Integer id);

    /**
     * 根据 parameterId 查询
     */
    TimeAxisEntity selectByParameterId(Integer parameterId);

    /**
     * 根据非空字段动态查询 TimeAxis
     *
     * @param entity 查询条件，非空字段生效
     * @return TimeAxisSEntity 列表
     */
    List<TimeAxisEntity> selectByCondition(TimeAxisEntity entity);
}
