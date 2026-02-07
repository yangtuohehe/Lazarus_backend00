package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DynamicProcessModelDao {

    /**
     * 插入 - 方法名统一为 insert，去掉 @Param
     */
    int insert(DynamicProcessModelEntity entity);

    /**
     * 根据 ID 查询
     */
    DynamicProcessModelEntity selectById(Integer id);

    /**
     * 动态查询 - 去掉 @Param
     */
    List<DynamicProcessModelEntity> selectByCondition(DynamicProcessModelEntity entity);


    // ========== 新增：二进制大文件专用通道 ==========
    /**
     * 修改 1：返回 Entity
     * 仅查询模型二进制文件 (BLOB)
     */
    DynamicProcessModelEntity selectModelFile(@Param("id") Integer id);

    /**
     * 修改 2：返回 Entity
     * 【嗅探专用】只查询文件的前 16 个字节
     */
    DynamicProcessModelEntity selectModelHeader(@Param("id") Integer id);


    /**
     * 专门用于更新指定 ID 的模型文件
     */
    @Update("UPDATE dynamic_process_model SET model_file = #{fileData} WHERE id = #{id}")
    int updateModelFile(@Param("id") Integer id, @Param("fileData") byte[] fileData);
}