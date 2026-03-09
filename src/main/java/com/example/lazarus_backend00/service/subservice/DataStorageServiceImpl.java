package com.example.lazarus_backend00.service.subservice;

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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class DataStorageServiceImpl implements DataStorageService {

    private static final String DB_ROOT = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";
    private static final DateTimeFormatter FMT_DAILY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FMT_HOURLY = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC);

    private final FeatureMetadataManager featureManager;

    public DataStorageServiceImpl(FeatureMetadataManager featureManager) {
        this.featureManager = featureManager;
    }

    // =================================================================
    // 1. 状态检查 (保持不变)
    // =================================================================
    @Override
    public List<DataCheckResult> checkDataStatus(List<TSShell> shells) {
        List<DataCheckResult> results = new ArrayList<>();
        for (TSShell shell : shells) {
            int fid = shell.getFeatureId();
            String folderName = featureManager.getFolderName(fid);
            FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);
            List<Instant> timePoints = expandTimePoints(shell);

            for (Instant targetTime : timePoints) {
                String realFileName = generateFileName(folderName, strategy, targetTime, "");
                Path realPath = Paths.get(DB_ROOT, folderName, realFileName);
                if (Files.exists(realPath)) {
                    results.add(new DataCheckResult(fid, targetTime, 1, realFileName));
                    continue;
                }
                String simFileName = generateFileName(folderName, strategy, targetTime, "-ls");
                Path simPath = Paths.get(DB_ROOT, folderName, simFileName);
                if (Files.exists(simPath)) {
                    results.add(new DataCheckResult(fid, targetTime, 2, simFileName));
                } else {
                    results.add(new DataCheckResult(fid, targetTime, 0, null));
                }
            }
        }
        return results;
    }

// =================================================================
    // 2. 批量与单点数据读取 (严格模式：找不到文件直接抛异常)
    // =================================================================
    @Override
    public List<TSDataBlock> fetchDataBlocks(List<TSShell> shells) {
        List<TSDataBlock> results = new ArrayList<>();
        for (TSShell shell : shells) {
            try {
                int fid = shell.getFeatureId();
                String folderName = featureManager.getFolderName(fid);
                FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);

                List<Instant> timePoints = expandTimePoints(shell);
                int tCount = timePoints.size();
                int width = (shell.hasSpace()) ? shell.getXAxis().getCount() : 1;
                int height = (shell.hasSpace()) ? shell.getYAxis().getCount() : 1;
                int frameSize = width * height;
                int totalSize = tCount * frameSize;

                float[] flattenedData = new float[totalSize];

                // 🗑️ 删除了之前那句掩耳盗铃的 Arrays.fill(flattenedData, Float.NaN);

                for (int t = 0; t < tCount; t++) {
                    Instant currentTime = timePoints.get(t);
                    Path targetPath = null;
                    String realName = generateFileName(folderName, strategy, currentTime, "");
                    Path realPath = Paths.get(DB_ROOT, folderName, realName);

                    if (Files.exists(realPath)) {
                        targetPath = realPath;
                    } else {
                        String simName = generateFileName(folderName, strategy, currentTime, "-ls");
                        Path simPath = Paths.get(DB_ROOT, folderName, simName);
                        if (Files.exists(simPath)) targetPath = simPath;
                    }

                    if (targetPath != null) {
                        readGeoTiffIntoArray(targetPath.toFile(), flattenedData, t * frameSize, frameSize);
                    } else {
                        // 🚨 核心修改：如果既没有实测文件，也没有仿真文件，绝对不塞 NaN，直接抛出致命异常！
                        String errorMsg = String.format("严重错误：底层数据缺失！无法找到特征 [%s] 在时刻 [%s] 的实测或仿真文件！",
                                folderName, currentTime.toString());
                        System.err.println(errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }
                }

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
                // 🚨 核心修改：不再只打印 e.printStackTrace() 后默默继续，而是向上层抛出异常中断执行！
                throw new RuntimeException("Data subsystem fetch failed: " + e.getMessage(), e);
            }
        }
        return results;
    }

    @Override
    public TSDataBlock fetchSingleDataBlock(TSShell shell) {
        List<TSDataBlock> blocks = fetchDataBlocks(Collections.singletonList(shell));
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    // =================================================================
    // 3. 模型仿真结果入库 (强制 -ls 后缀 + NaN 拦截防毒机制)
    // =================================================================
    @Override
    public void ingestCalculatedData(TSDataBlock block) {
        String suffix = "-ls";
        try {
            int fid = block.getFeatureId();
            String folderName = featureManager.getFolderName(fid);
            FeatureMetadataManager.NamingStrategy strategy = featureManager.getStrategy(folderName);
            Path folderPath = Paths.get(DB_ROOT, folderName);

            if (!Files.exists(folderPath)) Files.createDirectories(folderPath);

            int width = block.getXAxis().getCount();
            int height = block.getYAxis().getCount();
            int frameSize = width * height;

            double minX = block.getXOrigin();
            double maxX = minX + (width * block.getXAxis().getResolution());
            double minY = block.getYOrigin();
            double maxY = minY + (height * block.getYAxis().getResolution());
            ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, DefaultGeographicCRS.WGS84);

            List<Instant> timePoints = expandTimePointsFromBlock(block);
            float[] allData = block.getData();

            for (int t = 0; t < timePoints.size(); t++) {
                Instant currentTime = timePoints.get(t);
                String fileName = generateFileName(folderName, strategy, currentTime, suffix);
                File outputFile = folderPath.resolve(fileName).toFile();

                float[] frameData = new float[frameSize];
                int srcPos = t * frameSize;

                if (srcPos + frameSize > allData.length) {
                    System.err.println("数据长度不足，跳过第 " + t + " 帧");
                    continue;
                }
                System.arraycopy(allData, srcPos, frameData, 0, frameSize);

                // =======================================================
                // 🛑 核心防线：检查这帧数据是不是全被掩膜置为了 NaN
                // =======================================================
                boolean hasValidPixel = false;
                for (float v : frameData) {
                    if (!Float.isNaN(v)) {
                        hasValidPixel = true;
                        break; // 只要发现一颗有效的像素，就判定这帧有效
                    }
                }

                // 如果这帧 100% 都是 NaN (完全被掩膜丢弃的垃圾数据)，绝不写入磁盘！
                if (!hasValidPixel) {
                    System.out.println("   ⏭️ [DataStorage] 拦截废弃帧！时刻 " + currentTime + " 被掩膜标记为全 NaN，拒绝写入磁盘以免污染下一轮输入！");
                    continue; // 💥 直接跳过，跳到下一帧！
                }
                // =======================================================

                writeGeoTiff(outputFile, frameData, width, height, envelope);
                System.out.println("   💾 [Calculated] 已保存有效仿真数据: " + outputFile.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("仿真数据入库失败: " + e.getMessage(), e);
        }
    }
    // ================== 辅助工具方法 (保持不变) ==================
    private String generateFileName(String folderName, FeatureMetadataManager.NamingStrategy strategy, Instant time, String suffix) {
        String baseName = (strategy == FeatureMetadataManager.NamingStrategy.DAILY_SHORT)
                ? FMT_DAILY.format(time) : FMT_HOURLY.format(time);
        return baseName + suffix + ".tif";
    }

    private List<Instant> expandTimePoints(TSShell shell) {
        List<Instant> points = new ArrayList<>();
        if (shell.hasTime() && shell.getTAxis().getCount() != null) {
            Instant base = shell.getTOrigin();

            // 🎯 核心修复：调用你下面已经写好的单位转换方法！1小时会被正确转换为 3600秒
            long stepSeconds = getAxisResolutionInSeconds(shell.getTAxis());

            for (int i = 0; i < shell.getTAxis().getCount(); i++) {
                points.add(base.plusSeconds(i * stepSeconds)); // 👈 这样加的才是 3600秒，7200秒...
            }
        } else if (shell.getTOrigin() != null) {
            points.add(shell.getTOrigin());
        }
        return points;
    }

    private List<Instant> expandTimePointsFromBlock(TSDataBlock block) {
        List<Instant> points = new ArrayList<>();
        if (block.getTAxis() != null && block.getTAxis().getCount() != null) {
            Instant base = block.getTOrigin();

            // 🎯 核心修复：将带有单位的分辨率，转换为真实的秒数跨度
            long stepSeconds = getAxisResolutionInSeconds(block.getTAxis());

            for (int i = 0; i < block.getTAxis().getCount(); i++) {
                // 用转换后的秒数进行推演
                points.add(base.plusSeconds(i * stepSeconds));
            }
        } else {
            points.add(block.getTOrigin());
        }
        return points;
    }
    /**
     * 将时间轴的单位统一转换为秒，用于子系统切片写文件
     */
    private long getAxisResolutionInSeconds(com.example.lazarus_backend00.domain.axis.TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "";

        // 🎯 强校验与硬编码：只要看到 h 开头，或者单位为空，统统按小时 (3600秒) 算！
        if (unit.startsWith("h") || unit.isEmpty()) {
            return (long) (res * 3600);
        } else if (unit.startsWith("m")) {
            return (long) (res * 60);
        }
        // 如果明确传了 Seconds，才按秒算
        return (long) res;
    }

    private void readGeoTiffIntoArray(File file, float[] target, int offset, int length) {
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        if (format == null) return;
        GridCoverage2DReader reader = null;
        GridCoverage2D coverage = null;
        try {
            reader = format.getReader(file);
            if (reader == null) return;
            coverage = reader.read(null);
            if (coverage == null) return;

            RenderedImage image = coverage.getRenderedImage();
            DataBuffer dataBuffer = image.getData().getDataBuffer();
            int readLength = Math.min(dataBuffer.getSize(), length);

            for (int i = 0; i < readLength; i++) {
                target[offset + i] = dataBuffer.getElemFloat(i);
            }
        } catch (Exception e) {
            System.err.println("❌ [GeoTiff] 读取异常 [" + file.getName() + "]: " + e.getMessage());
        } finally {
            if (coverage != null) try { coverage.dispose(true); } catch (Exception ignored) {}
            if (reader != null) try { reader.dispose(); } catch (Exception ignored) {}
        }
    }

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