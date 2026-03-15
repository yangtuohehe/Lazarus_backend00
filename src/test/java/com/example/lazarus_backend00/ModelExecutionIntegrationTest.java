package com.example.lazarus_backend00;

import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.service.ModelContainerProvider;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.media.jai.RasterFactory;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
public class ModelExecutionIntegrationTest {

    @Autowired
    private ModelContainerProvider containerProvider;

    private static final String BASE_DATA_DIR = "D:\\CODE\\project\\Lazarus\\Data\\cae-rbnnmodel\\";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2023, 1, 1, 0, 0);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MODEL_ID = 15;
    private static final int INTERFACE_ID = 19;

    @Test
    public void testGanquanMultiStepModelExecution() {
        System.out.println("[Test Start] Ganquan Island Multi-step Prediction Model Verification");

        ModelContainer container = null;
        try {
            System.out.println("1. Reconstructing model container from metadata...");
            container = containerProvider.reconstructContainer(MODEL_ID, INTERFACE_ID);

            System.out.println("2. Loading ONNX inference engine...");
            if (!container.load()) {
                throw new RuntimeException("Model container load() failed");
            }

            System.out.println("3. Reading TIF and cropping edges...");
            Instant baseInstant = BASE_TIME.toInstant(ZoneOffset.UTC);

            TSDataBlock ndviBlock = loadTiffToTsDataBlock(BASE_DATA_DIR + "ndvi/"
                    + BASE_TIME.format(TIME_FORMATTER) + ".tif", 12, baseInstant);

            float[] t2mSeq = new float[3];
            float[] radSeq = new float[3];
            float[] rainSeq = new float[3];

            for (int i = 0; i < 3; i++) {
                String tStr = BASE_TIME.plusMonths(i).format(TIME_FORMATTER);
                t2mSeq[i] = loadTiffToTsDataBlock(BASE_DATA_DIR + "t2m/" + tStr + ".tif", 13, baseInstant).getData()[0];
                radSeq[i] = loadTiffToTsDataBlock(BASE_DATA_DIR + "irradiation/" + tStr + ".tif", 14, baseInstant).getData()[0];
                rainSeq[i] = loadTiffToTsDataBlock(BASE_DATA_DIR + "rainfall/" + tStr + ".tif", 15, baseInstant).getData()[0];
            }

            TSDataBlock t2mBlock = new TSDataBlock.Builder().featureId(13).time(baseInstant, null)
                    .x(ndviBlock.getXOrigin(), ndviBlock.getXAxis()).y(ndviBlock.getYOrigin(), ndviBlock.getYAxis()).data(t2mSeq).build();
            TSDataBlock radBlock = new TSDataBlock.Builder().featureId(14).time(baseInstant, null)
                    .x(ndviBlock.getXOrigin(), ndviBlock.getXAxis()).y(ndviBlock.getYOrigin(), ndviBlock.getYAxis()).data(radSeq).build();
            TSDataBlock rainBlock = new TSDataBlock.Builder().featureId(15).time(baseInstant, null)
                    .x(ndviBlock.getXOrigin(), ndviBlock.getXAxis()).y(ndviBlock.getYOrigin(), ndviBlock.getYAxis()).data(rainSeq).build();

            List<List<TSDataBlock>> inputGroups = Arrays.asList(Arrays.asList(ndviBlock), Arrays.asList(t2mBlock, radBlock, rainBlock));

            System.out.println("4. Executing container inference...");
            long startTime = System.currentTimeMillis();
            List<List<TSDataBlock>> outputGroups = container.run(inputGroups);
            System.out.println("Execution completed. Time cost: " + (System.currentTimeMillis() - startTime) + " ms");

            System.out.println("5. Generating output TIFs...");
            String outputDir = BASE_DATA_DIR + "ndvi_output/";
            new File(outputDir).mkdirs();

            TSDataBlock outBlock = outputGroups.get(0).get(0);

            for (int step = 1; step <= 3; step++) {
                LocalDateTime targetTime = LocalDateTime.ofInstant(baseInstant, ZoneOffset.UTC).plusMonths(step);
                String targetTimeStr = targetTime.format(TIME_FORMATTER);

                String outputTiffPath = outputDir + targetTimeStr + "-ls.tif";

                writeOutputsWithTemplate(outBlock, (step - 1), ndviBlock, outputTiffPath);

                System.out.println("   --> Generated file: " + outputTiffPath);
            }
            System.out.println("[Test Success] Full pipeline execution finished.");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[Test Failed] " + e.getMessage());
        } finally {
            if (container != null) container.unload();
        }
    }

    private TSDataBlock loadTiffToTsDataBlock(String filePath, int featureId, Instant time) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) throw new Exception("File not found: " + filePath);

        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        GridCoverage2DReader reader = format.getReader(file);
        GridCoverage2D coverage = reader.read(null);

        ReferencedEnvelope env = new ReferencedEnvelope(coverage.getEnvelope());
        double minX = env.getMinX();
        double maxX = env.getMaxX();
        double minY = env.getMinY();
        double maxY = env.getMaxY();

        RenderedImage image = coverage.getRenderedImage();
        int width = image.getWidth();
        int height = image.getHeight();
        double resX = (maxX - minX) / width;
        double resY = (maxY - minY) / height;

        DataBuffer dataBuffer = image.getData().getDataBuffer();

        int targetWidth = width > 1 ? width - 1 : width;
        int targetHeight = height > 1 ? height - 1 : height;
        float[] data = new float[targetWidth * targetHeight];

        if (width > 1 && height > 1) {
            int idx = 0;
            for (int y = 0; y < height - 1; y++) {
                for (int x = 0; x < width - 1; x++) {
                    float val = dataBuffer.getElemFloat(y * width + x);
                    if (Float.isNaN(val) || val < -1000f) {
                        val = 0.0f;
                    }
                    data[idx++] = val;
                }
            }
            minY = minY + resY;
        } else {
            for (int i = 0; i < data.length; i++) {
                float val = dataBuffer.getElemFloat(i);
                if (Float.isNaN(val) || val < -1000f) val = 0.0f;
                data[i] = val;
            }
        }

        reader.dispose();
        coverage.dispose(true);

        SpaceAxisX xAxis = new SpaceAxisX(resX * targetWidth, "Degrees", resX, "Degrees");
        xAxis.setCount(targetWidth);

        SpaceAxisY yAxis = new SpaceAxisY(resY * targetHeight, "Degrees", resY, "Degrees");
        yAxis.setCount(targetHeight);

        TimeAxis tAxis = new TimeAxis(1.0, "Months", 1.0, "Months");
        tAxis.setCount(1);
        tAxis.setType("TIME");

        return new TSDataBlock.Builder()
                .featureId(featureId)
                .time(time, tAxis)
                .x(minX, xAxis)
                .y(minY, yAxis)
                .data(data)
                .build();
    }

    private void writeOutputsWithTemplate(TSDataBlock outBlock, int timeSliceIndex, TSDataBlock templateBlock, String tiffPath) throws Exception {
        int width = templateBlock.getXAxis().getCount();
        int height = templateBlock.getYAxis().getCount();
        int frameSize = width * height;

        float[] frameData = new float[frameSize];
        System.arraycopy(outBlock.getData(), timeSliceIndex * frameSize, frameData, 0, frameSize);

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float v : frameData) {
            if (!Float.isNaN(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        System.out.println("   [Data Check] Frame " + (timeSliceIndex + 1) + " Extremes: Min=" + min + ", Max=" + max);

        double minX = templateBlock.getXOrigin();
        double maxX = minX + (width * templateBlock.getXAxis().getResolution());
        double minY = templateBlock.getYOrigin();
        double maxY = minY + (height * templateBlock.getYAxis().getResolution());

        ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, DefaultGeographicCRS.WGS84);

        WritableRaster tiffRaster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT, width, height, 1, null);
        tiffRaster.setSamples(0, 0, width, height, 0, frameData);

        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D coverage = factory.create("data", tiffRaster, envelope);

        GeoTiffWriter writer = new GeoTiffWriter(new File(tiffPath));
        writer.write(coverage, null);
        writer.dispose();
    }
}