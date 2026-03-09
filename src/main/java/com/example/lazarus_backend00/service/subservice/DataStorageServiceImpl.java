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
    // 1. Status Check (Unchanged)
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
    // 2. Batch and Single Data Block Fetching (Strict mode: throw exception if file not found)
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

                // 🗑️ Removed the previous self-deceiving Arrays.fill(flattenedData, Float.NaN);

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
                        // 🚨 Core modification: If neither observed nor simulated files exist, absolutely do not fill with NaN. Throw a fatal exception instead!
                        String errorMsg = String.format("Fatal Error: Underlying data missing! Cannot find observed or simulated file for feature [%s] at time [%s]!",
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
                // 🚨 Core modification: No longer silently continuing after printing e.printStackTrace(), but throwing the exception to the upper layer to interrupt execution!
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
    // 3. Ingest Model Simulation Results (Forced -ls suffix + NaN intercept anti-poison mechanism)
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
                    System.err.println("Data length insufficient, skipping frame " + t);
                    continue;
                }
                System.arraycopy(allData, srcPos, frameData, 0, frameSize);

                // =======================================================
                // 🛑 Core defense line: Check if this frame of data is completely masked with NaN
                // =======================================================
                boolean hasValidPixel = false;
                for (float v : frameData) {
                    if (!Float.isNaN(v)) {
                        hasValidPixel = true;
                        break; // As long as one valid pixel is found, this frame is considered valid
                    }
                }

                // If this frame is 100% NaN (garbage data completely discarded by the mask), never write it to disk!
                if (!hasValidPixel) {
                    System.out.println("   ⏭️ [DataStorage] Intercepted discarded frame! Time " + currentTime + " is masked as all NaN, refusing to write to disk to avoid polluting the next round of input!");
                    continue; // 💥 Skip directly to the next frame!
                }
                // =======================================================

                writeGeoTiff(outputFile, frameData, width, height, envelope);
                System.out.println("   💾 [Calculated] Saved valid simulation data: " + outputFile.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ingest simulation data: " + e.getMessage(), e);
        }
    }

    // ================== Auxiliary Tool Methods (Unchanged) ==================
    private String generateFileName(String folderName, FeatureMetadataManager.NamingStrategy strategy, Instant time, String suffix) {
        String baseName = (strategy == FeatureMetadataManager.NamingStrategy.DAILY_SHORT)
                ? FMT_DAILY.format(time) : FMT_HOURLY.format(time);
        return baseName + suffix + ".tif";
    }

    private List<Instant> expandTimePoints(TSShell shell) {
        List<Instant> points = new ArrayList<>();
        if (shell.hasTime() && shell.getTAxis().getCount() != null) {
            Instant base = shell.getTOrigin();

            // 🎯 Core fix: Call the unit conversion method already written below! 1 hour will be correctly converted to 3600 seconds
            long stepSeconds = getAxisResolutionInSeconds(shell.getTAxis());

            for (int i = 0; i < shell.getTAxis().getCount(); i++) {
                points.add(base.plusSeconds(i * stepSeconds)); // 👈 This way it correctly adds 3600s, 7200s...
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

            // 🎯 Core fix: Convert the resolution with units into a true span of seconds
            long stepSeconds = getAxisResolutionInSeconds(block.getTAxis());

            for (int i = 0; i < block.getTAxis().getCount(); i++) {
                // Deduce using the converted seconds
                points.add(base.plusSeconds(i * stepSeconds));
            }
        } else {
            points.add(block.getTOrigin());
        }
        return points;
    }

    /**
     * Uniformly convert the unit of the time axis to seconds, used for subsystem slicing and writing files
     */
    private long getAxisResolutionInSeconds(com.example.lazarus_backend00.domain.axis.TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "";

        // 🎯 Strong validation and hardcoding: As long as it starts with 'h', or the unit is empty, always calculate as hours (3600 seconds)!
        if (unit.startsWith("h") || unit.isEmpty()) {
            return (long) (res * 3600);
        } else if (unit.startsWith("m")) {
            return (long) (res * 60);
        }
        // Only calculate as seconds if 'Seconds' is explicitly passed
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
            System.err.println("❌ [GeoTiff] Read exception [" + file.getName() + "]: " + e.getMessage());
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