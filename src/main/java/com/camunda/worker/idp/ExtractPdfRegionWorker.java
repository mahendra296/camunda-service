package com.camunda.worker.idp;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Crops a rectangular region out of one page of a PDF into a JPEG using the {@code pdftoppm}
 * command-line tool (part of poppler-utils). The executable path is configurable via {@code
 * idp.pdftoppm-path} (env var {@code IDP_PDFTOPPM_PATH}), defaulting to the bare {@code pdftoppm}
 * command, which only resolves if poppler's bin directory is on this process's PATH. The crop
 * coordinates ({@code pageNumber}, {@code xPosition}, {@code yPosition}) are pixel offsets at the
 * given render resolution, not PDF points — e.g. at the default 150 dpi, 1 inch = 150 px. {@code
 * width}/{@code height} are optional; when absent, {@code idp.crop-region.default-width}/{@code
 * default-height} are used, since callers like the signature-detection flow only know the
 * top-left corner of the region.
 *
 * <p>The output image is written next to the source PDF, as {@code <pdfBaseName>_extract.jpg}.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code extractedImagePath} — absolute path to the cropped JPEG
 * </ul>
 */
@Slf4j
@Component
public class ExtractPdfRegionWorker {

    private final String pdftoppmPath;
    private final int defaultCropWidth;
    private final int defaultCropHeight;

    @Autowired
    public ExtractPdfRegionWorker(
            @Value("${idp.pdftoppm-path:pdftoppm}") String pdftoppmPath,
            @Value("${idp.crop-region.default-width:300}") int defaultCropWidth,
            @Value("${idp.crop-region.default-height:150}") int defaultCropHeight) {
        this.pdftoppmPath = pdftoppmPath;
        this.defaultCropWidth = defaultCropWidth;
        this.defaultCropHeight = defaultCropHeight;
    }

    @JobWorker(type = "idp.extract-pdf-region", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String documentPath,
            @Variable("pageNumber") int page,
            @Variable("xPosition") int x,
            @Variable("yPosition") int y,
            @Variable("width") Integer width,
            @Variable("height") Integer height,
            @Variable Integer resolution) {

        var cropWidth = width != null ? width : defaultCropWidth;
        var cropHeight = height != null ? height : defaultCropHeight;
        log.info(
                "[IDP][ExtractPdfRegion] type={} key={} documentPath={} page={} x={} y={} width={} height={}",
                job.getType(),
                job.getKey(),
                documentPath,
                page,
                x,
                y,
                cropWidth,
                cropHeight);

        try {
            var sourcePath = Path.of(documentPath);
            var baseName = sourcePath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            var outputPrefix = sourcePath.resolveSibling(baseName + "_extract");
            var dpi = resolution != null ? resolution : 200;

            var command = List.of(
                    pdftoppmPath,
                    "-f",
                    String.valueOf(page),
                    "-l",
                    String.valueOf(page),
                    "-x",
                    String.valueOf(x),
                    "-y",
                    String.valueOf(y),
                    "-W",
                    String.valueOf(cropWidth),
                    "-H",
                    String.valueOf(cropHeight),
                    "-r",
                    String.valueOf(dpi),
                    "-jpeg",
                    "-singlefile",
                    sourcePath.toString(),
                    outputPrefix.toString());

            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            var exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("pdftoppm exited with code " + exitCode + ": " + output);
            }

            var extractedImagePath = outputPrefix + ".jpg";

            log.info(
                    "[IDP][ExtractPdfRegion] Cropped page={} region=({},{},{}x{}) -> {}",
                    page,
                    x,
                    y,
                    cropWidth,
                    cropHeight,
                    extractedImagePath);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("extractedImagePath", extractedImagePath))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[IDP][ExtractPdfRegion] Failed documentPath={} page={}", documentPath, page, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
