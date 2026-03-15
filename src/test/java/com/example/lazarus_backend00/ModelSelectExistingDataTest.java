package com.example.lazarus_backend00;

import com.example.lazarus_backend00.service.ModelSelectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class ModelSelectExistingDataTest {

    @Autowired
    private ModelSelectService selectService;

    @Test
    @DisplayName("直接查询数据库中已存在的真实数据")
    void testQueryExistingData() {

        // =================================================================
        // 1. 测试接口一：多条件组合查询模型层级
        // =================================================================
        // ⚠️ 请修改下面的 "盐度" 为你数据库里真实存在的模型名称
        String searchName = "东岛";
        String searchPaper = null; // 论文来源，不想测传 null
        String searchAuthor = null; // 作者，不想测传 null

        System.out.println(">>> [Test 1] 正在查询模型: " + searchName);

        String jsonResult = selectService.selectModelHierarchyIds(searchName, searchPaper, searchAuthor);

        System.out.println("   查询结果(JSON): " + jsonResult);
        System.out.println("------------------------------------------------------");


        // =================================================================
        // 2. 测试接口二：按特征名称 + IO类型反查模型
        // =================================================================
        // ⚠️ 请修改 "temp" 为你数据库里真实存在的特征名
        String searchFeature = "temp";
        String searchIoType = "INPUT"; // "INPUT" 或 "OUTPUT"，或 null (查全部)

        System.out.println(">>> [Test 2] 正在反查特征: " + searchFeature + " (" + searchIoType + ")");

        String jsonResult1 = selectService.selectModelsByFeatureNameAndIoType(searchFeature, searchIoType);

        System.out.println("   查询结果(JSON): " + jsonResult1);
        System.out.println("------------------------------------------------------");


        // =================================================================
        // 3. 测试接口三：按时空条件反查模型
        // =================================================================
        // ⚠️ 请修改下面的 12 为你数据库里真实存在的时间步数 (Count)
        Integer targetTimeCount = 3;
        String targetIoType = "OUTPUT";

        System.out.println(">>> [Test 3] 正在反查时空条件: IO=" + targetIoType + ", TimeCount=" + targetTimeCount);

        String jsonResult2 = selectService.selectModelsByOutputParameterConditions(
                targetIoType, // ioType
                null,         // temporalResolutionValue (分辨率数值, 不测传null)
                null,         // temporalResolutionUnit (分辨率单位, 不测传null)
                targetTimeCount, // temporalCount (时间步数, 重点测试这个)
                null          // wktPolygon (空间范围WKT, 不测传null)
        );

        System.out.println("   查询结果(JSON): " + jsonResult2);
        System.out.println("------------------------------------------------------");
    }
}