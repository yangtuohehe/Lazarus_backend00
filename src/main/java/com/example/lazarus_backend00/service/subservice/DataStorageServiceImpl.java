package com.example.lazarus_backend00.service.subservice;

import com.example.lazarus_backend00.annotation.AuditAnnotations;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataCheckResult;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.springframework.stereotype.Service;

import javax.media.jai.RasterFactory;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DataStorageServiceImpl  implements DataStorageService{

    // 数据库根目录 (请根据实际情况修改)
    private static final String DB_ROOT = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";

    // 时间格式化器
    private static final DateTimeFormatter FMT_DAILY = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FMT_HOURLY = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneId.systemDefault());

    private final FeatureMetadataManager featureManager;

    public DataStorageServiceImpl(FeatureMetadataManager featureManager) {
        this.featureManager = featureManager;
    }

    // =================================================================
    // 接口 1: 状态检查 (Check Data Status)
    // 逻辑：优先检查实测数据(State=1)，如果不存在则检查模拟数据(State=2)，否则为空(State=0)
    // =================================================================
    public List<DataCheckResult> checkDataStatus(List<TSShell> shells) {
        List<DataCheckResult> results = new ArrayList<>();

        for (TSShell shell : shells) {
            int fid = shell.getFeatureId();
            String folderName = featureManager.getFolderName(fid);
            FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);

            // 解析时间点
            List<Instant> timePoints = expandTimePoints(shell);

            for (Instant targetTime : timePoints) {
                // 1. 优先检查实测数据 (无后缀)
                // 即使本类不负责写入实测数据，检查逻辑也必须包含它，确保数据优先级正确
                String realFileName = generateFileName(folderName, strategy, targetTime, "");
                Path realPath = Paths.get(DB_ROOT, folderName, realFileName);

                if (Files.exists(realPath)) {
                    // 发现实测数据 -> 返回 1
                    results.add(new DataCheckResult(fid, targetTime, 1, realFileName));
                    continue; // 找到高优先级的，直接跳过后续
                }

                // 2. 次选检查模拟数据 (后缀 -ls)
                String simFileName = generateFileName(folderName, strategy, targetTime, "-ls");
                Path simPath = Paths.get(DB_ROOT, folderName, simFileName);

                if (Files.exists(simPath)) {
                    // 发现模拟数据 -> 返回 2
                    results.add(new DataCheckResult(fid, targetTime, 2, simFileName));
                } else {
                    // 都没有 -> 返回 0
                    results.add(new DataCheckResult(fid, targetTime, 0, null));
                }
            }
        }
        return results;
    }

    // =================================================================
    // 接口 2: 数据读取 (Fetch Data Blocks)
    // 逻辑：如果有实测数据，读实测；如果没有但有模拟数据，读模拟。
    // =================================================================
    public List<TSDataBlock> fetchDataBlocks(List<TSShell> shells) {
        List<TSDataBlock> results = new ArrayList<>();

        for (TSShell shell : shells) {
            try {
                int fid = shell.getFeatureId();
                String folderName = featureManager.getFolderName(fid);
                FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);

                List<Instant> timePoints = expandTimePoints(shell);
                int tCount = timePoints.size();

                // 维度计算 (假设为 2D+Time, 若无空间维度则默认为1)
                int width = (shell.hasSpace()) ? shell.getXAxis().getCount() : 1;
                int height = (shell.hasSpace()) ? shell.getYAxis().getCount() : 1;
                int frameSize = width * height;
                int totalSize = tCount * frameSize;

                float[] flattenedData = new float[totalSize];
                Arrays.fill(flattenedData, Float.NaN); // 默认填充 NaN

                for (int t = 0; t < tCount; t++) {
                    Instant currentTime = timePoints.get(t);
                    Path targetPath = null;

                    // A. 尝试获取实测文件 (无后缀)
                    String realName = generateFileName(folderName, strategy, currentTime, "");
                    Path realPath = Paths.get(DB_ROOT, folderName, realName);

                    if (Files.exists(realPath)) {
                        targetPath = realPath;
                    } else {
                        // B. 尝试获取模拟文件 (-ls)
                        String simName = generateFileName(folderName, strategy, currentTime, "-ls");
                        Path simPath = Paths.get(DB_ROOT, folderName, simName);
                        if (Files.exists(simPath)) {
                            targetPath = simPath;
                        }
                    }

                    // C. 读取并填充
                    if (targetPath != null) {
                        readGeoTiffIntoArray(targetPath.toFile(), flattenedData, t * frameSize, frameSize);
                    }
                }

                // 组装 Block
                TSDataBlock block = new TSDataBlock.Builder()
                        .featureId(fid)
                        .data(flattenedData)
                        .time(shell.getTOrigin(), shell.getTAxis())
                        .z(shell.getZOrigin(), shell.getZAxis())
                        .y(shell.getYOrigin(), shell.getYAxis())
                        .x(shell.getXOrigin(), shell.getXAxis())
                        .build();

                results.add(block);

            } catch (Exception e) {
                e.printStackTrace();
                // 实际生产中建议 log.error(...)
            }
        }
        return results;
    }

    // =================================================================
    // 接口 3: 模型计算结果入库 (Ingest Calculated Data Only)
    // 逻辑：强制添加 "-ls" 后缀
    // =================================================================
    @AuditAnnotations.LogResultWriteBack
    public void ingestCalculatedData(TSDataBlock block) {
        // 固定后缀 "-ls"
        String suffix = "-ls";

        try {
            int fid = block.getFeatureId();
            String folderName = featureManager.getFolderName(fid);
            FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);
            Path folderPath = Paths.get(DB_ROOT, folderName);

            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            int width = block.getXAxis().getCount();
            int height = block.getYAxis().getCount();
            int frameSize = width * height;

            // 构建地理围栏 (Envelope)
            double minX = block.getXOrigin();
            double maxX = minX + (width * block.getXAxis().getResolution());
            double minY = block.getYOrigin();
            double maxY = minY + (height * block.getYAxis().getResolution());
            ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, DefaultGeographicCRS.WGS84);

            // 解析时间点
            List<Instant> timePoints = expandTimePointsFromBlock(block);
            float[] allData = block.getData();

            for (int t = 0; t < timePoints.size(); t++) {
                Instant currentTime = timePoints.get(t);

                // 🔥 生成带 -ls 后缀的文件名
                String fileName = generateFileName(folderName, strategy, currentTime, suffix);
                File outputFile = folderPath.resolve(fileName).toFile();

                // 提取单帧数据
                float[] frameData = new float[frameSize];
                int srcPos = t * frameSize;

                // 简单的边界检查
                if (srcPos + frameSize > allData.length) {
                    System.err.println("数据长度不足，跳过第 " + t + " 帧");
                    continue;
                }
                System.arraycopy(allData, srcPos, frameData, 0, frameSize);

                // 写入 TIF
                writeGeoTiff(outputFile, frameData, width, height, envelope);
                System.out.println("   💾 [Calculated] 已保存仿真数据: " + outputFile.getName());
            }

        } catch (Exception e) {
            throw new RuntimeException("仿真数据入库失败: " + e.getMessage(), e);
        }
    }

    // =================================================================
    // 核心工具方法 (Core Utils)
    // =================================================================

    /**
     * 生成文件名 (统一格式：时间字符串 + 后缀 + .tif)
     */
    private String generateFileName(String folderName, FeatureMetadataManager.NamingStrategy strategy, Instant time, String suffix) {
        String baseName;
        if (strategy == FeatureMetadataManager.NamingStrategy.DAILY_SHORT) {
            baseName = FMT_DAILY.format(time);
        } else {
            // 统一为纯时间格式
            baseName = FMT_HOURLY.format(time);
        }
        return baseName + suffix + ".tif";
    }

    private List<Instant> expandTimePoints(TSShell shell) {
        List<Instant> points = new ArrayList<>();
        if (shell.hasTime()) {
            Instant base = shell.getTOrigin();
            double res = shell.getTAxis().getResolution();
            for (int i = 0; i < shell.getTAxis().getCount(); i++) {
                points.add(base.plusSeconds((long)(i * res)));
            }
        } else if (shell.getTOrigin() != null) {
            points.add(shell.getTOrigin());
        }
        return points;
    }

    private List<Instant> expandTimePointsFromBlock(TSDataBlock block) {
        List<Instant> points = new ArrayList<>();
        if (block.getTAxis() != null) {
            Instant base = block.getTOrigin();
            double res = block.getTAxis().getResolution();
            for (int i = 0; i < block.getTAxis().getCount(); i++) {
                points.add(base.plusSeconds((long)(i * res)));
            }
        } else {
            points.add(block.getTOrigin());
        }
        return points;
    }

    /**
     * GeoTools 读取实现
     */
    private void readGeoTiffIntoArray(File file, float[] target, int offset, int length) {
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        if (format == null) {
            System.err.println("❌ [GeoTiff] 无法识别文件格式: " + file.getName());
            return;
        }

        GridCoverage2DReader reader = null;
        GridCoverage2D coverage = null;
        try {
            reader = format.getReader(file);

            // 🔥 修复点 1: 如果 reader 初始化失败(比如上面的EPSG错误)，这里可能是 null
            if (reader == null) {
                System.err.println("❌ [GeoTiff] 获取 Reader 失败: " + file.getName());
                return;
            }

            coverage = reader.read(null);
            // 🔥 修复点 2: coverage 也可能为空
            if (coverage == null) {
                System.err.println("❌ [GeoTiff] 读取 Coverage 失败: " + file.getName());
                return;
            }

            RenderedImage image = coverage.getRenderedImage();
            DataBuffer dataBuffer = image.getData().getDataBuffer();

            int readLength = Math.min(dataBuffer.getSize(), length);

            // 假设是 float 数据
            for (int i = 0; i < readLength; i++) {
                target[offset + i] = dataBuffer.getElemFloat(i);
            }

        } catch (Exception e) {
            // 🔥 修复点 3: 捕获异常后打印明确日志，不要让 NPE 掩盖了真实错误
            System.err.println("❌ [GeoTiff] 读取异常 [" + file.getName() + "]: " + e.getMessage());
            // e.printStackTrace(); // 调试时可以打开
        } finally {
            // 释放资源
            if (coverage != null) {
                try { coverage.dispose(true); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.dispose(); } catch (Exception ignored) {}
            }
        }
    }
    /**
     * GeoTools 写入实现
     */
    private void writeGeoTiff(File file, float[] data, int width, int height, ReferencedEnvelope env) throws Exception {
        WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT, width, height, 1, null);
        raster.setSamples(0, 0, width, height, 0, data);

        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D coverage = factory.create("data", raster, env);

        GeoTiffWriter writer = null;
        try {
            writer = new GeoTiffWriter(file);
            writer.write(coverage, null);
        } finally {
            if (writer != null) writer.dispose();
        }
    }
}