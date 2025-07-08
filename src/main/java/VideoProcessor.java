import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

public class VideoProcessor {

    /*public static void processVideoWithTempFrames(String inputVideo, String outputVideo,
                                                  FilterType filter, ImageProcessor processorType, int numThreads)
            throws IOException, InterruptedException {

        Path tempDir = Files.createTempDirectory("video_frames_");

        try {
            // 1. Apply filter immediately during extraction
            extractAndFilterFrames(inputVideo, tempDir, filter, processorType, numThreads);

            // 2. Encode the filtered frames
            encodeFramesToVideo(tempDir, outputVideo);

        } finally {
            // 3. Clean up
            deleteDirectoryRecursively(tempDir);
        }
    }

    private static void extractAndFilterFrames(String inputVideo, Path outputDir,
                                               FilterType filter, ImageProcessor processorType , int numThreads)
            throws IOException, InterruptedException {


        // Extract frames to output directory
        ProcessBuilder pb = new ProcessBuilder(
                FFMPEG_PATH,
                "-loglevel", "quiet",
                "-i", inputVideo,
                "-vf", "fps=25",
                outputDir.resolve("frame_%03d.png").toString()
        );

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process ffmpegProcess = pb.start();

        // Wait for extraction to finish
        int exit = ffmpegProcess.waitFor();
        if (exit != 0) throw new IOException("FFmpeg frame extraction failed.");

        // Apply filtering immediately on each extracted frame
        File[] frames = outputDir.toFile().listFiles((_, name) -> name.endsWith(".png"));
        if (frames == null) throw new IOException("No frames extracted!");

        for (File frame : frames) {
            BufferedImage original = ImageIO.read(frame);
            switch (processorType) {
            case SEQUENTIAL -> SequentialProcessing.applyFilter(filter, original);
            case FORKJOIN -> ForkJoinProcessing.applyFilter(filter, original, numThreads);
            case EXECUTOR -> ExecutorServiceProcessing.applyFilter(filter, original, numThreads);
            default -> throw new IllegalArgumentException("Unsupported processor type: " + processorType);
        }
            ImageIO.write(original, "png", frame);
        }
    }*/

    public static Path extractFrames(String inputVideo) throws IOException {
        Path tempDir = Files.createTempDirectory("video_frames_");
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputVideo);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            Frame frame;
            int frameNumber = 0;
            while ((frame = grabber.grabImage()) != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    File outFile = tempDir.resolve(String.format("frame_%03d.png", frameNumber++)).toFile();
                    ImageIO.write(img, "png", outFile);
                }
            }
            grabber.stop();
        } catch (Exception e) {
            throw new IOException("JavaCV frame extraction failed", e);
        }
        return tempDir;
    }

    public static Path copyFramesToTempDir(File[] frames, String suffix) throws IOException {
        Path tempDir = Files.createTempDirectory("video_frames_" + suffix + "_");
        for (File frame : frames) {
            Path dest = tempDir.resolve(frame.getName());
            Files.copy(frame.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempDir;
    }

    public static void processFramesInDir(Path dir, FilterType filter, ImageProcessor processorType, int numThreads, int blockSize) throws IOException {
        var frames = dir.toFile().listFiles((_, name) -> name.endsWith(".png"));
        if (frames == null) throw new IOException("No frames in directory: " + dir);
        for (File frame : frames) {
            BufferedImage original = ImageIO.read(frame);
            switch (processorType) {
                case SEQUENTIAL -> SequentialProcessing.applyFilter(filter, original);
                case FORKJOIN -> ForkJoinProcessing.applyFilter(filter, original, numThreads, blockSize);
                case EXECUTOR -> ExecutorServiceProcessing.applyFilter(filter, original, numThreads, blockSize);
                default -> throw new IllegalArgumentException("Unsupported processor type: " + processorType);
            }
            ImageIO.write(original, "png", frame);
        }
    }

    public static void encodeFramesToVideo(Path framesDir, String outputVideo)
            throws IOException {
        File[] frames = framesDir.toFile().listFiles((_, name) -> name.endsWith(".png"));
        if (frames == null || frames.length == 0) throw new IOException("No frames to encode");
        Arrays.sort(frames);
        BufferedImage first = ImageIO.read(frames[0]);
        int width = first.getWidth();
        int height = first.getHeight();
        int frameRate = 25;
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputVideo, width, height);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(frameRate);
            recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
            recorder.start();
            for (File frameFile : frames) {
                BufferedImage img = ImageIO.read(frameFile);
                Frame frame = converter.convert(img);
                recorder.record(frame);
            }
            recorder.stop();
        } catch (Exception e) {
            throw new IOException("JavaCV video encoding failed", e);
        }
    }

    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    // Overload for backward compatibility (if needed)
    public static void processFramesInDir(Path dir, FilterType filter, ImageProcessor processorType, int numThreads) throws IOException {
        processFramesInDir(dir, filter, processorType, numThreads, -1);
    }

    /**
     * Fully in-memory, pipelined video processing for maximum speedup.
     * No intermediate PNGs are written to disk.
     */
    public static void processVideoInMemory(String inputVideo, String outputVideo, FilterType filter, ImageProcessor processorType, int numThreads, int blockSize) throws IOException {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputVideo);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputVideo, 0, 0);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            int frameRate = (int) grabber.getFrameRate();
            if (frameRate <= 0) frameRate = 25;
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(frameRate);
            recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
            recorder.setImageWidth(width);
            recorder.setImageHeight(height);
            recorder.start();
            Frame frame;
            while ((frame = grabber.grabImage()) != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    // Filtering (parallel if needed)
                    switch (processorType) {
                        case SEQUENTIAL -> SequentialProcessing.applyFilter(filter, img);
                        case FORKJOIN -> ForkJoinProcessing.applyFilter(filter, img, numThreads, blockSize);
                        case EXECUTOR -> ExecutorServiceProcessing.applyFilter(filter, img, numThreads, blockSize);
                        default -> throw new IllegalArgumentException("Unsupported processor type: " + processorType);
                    }
                    Frame filteredFrame = converter.convert(img);
                    recorder.record(filteredFrame);
                }
            }
            grabber.stop();
            recorder.stop();
        } catch (Exception e) {
            throw new IOException("In-memory video processing failed", e);
        }
    }
}
