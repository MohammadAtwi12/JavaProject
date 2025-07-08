import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class App {

    public static void main(String[] args) {

        // IMAGE: speedup exploration
        try {
            // Load the input image
            BufferedImage originalImage = ImageIO.read(new File("nature.jpg"));
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            int maxThreads = Runtime.getRuntime().availableProcessors();
            System.out.println("threads,speedup_fk,speedup_ex");
            for (int numThreads = 1; numThreads <= maxThreads; numThreads++) {
                int numBlocks = numThreads * 2;
                int imageArea = width * height;
                int blockArea = Math.max(1, imageArea / numBlocks);
                int blockSize = (int) Math.ceil(Math.sqrt(blockArea));
                FilterType selectedFilter = FilterType.EDGE_DETECTION;
                BufferedImage seqImage = deepCopy(originalImage);
                BufferedImage parallelFKImage = deepCopy(originalImage);
                BufferedImage parallelExImage = deepCopy(originalImage);
                // Sequential timing
                long startSeq = System.nanoTime();
                SequentialProcessing.applyFilter(selectedFilter, seqImage);
                long endSeq = System.nanoTime();
                double timeSeqMs = (endSeq - startSeq) / 1_000_000.0;
                // Parallel (ForkJoin) timing
                long startParFk = System.nanoTime();
                ForkJoinProcessing.applyFilter(selectedFilter, parallelFKImage, numThreads, blockSize);
                long endParFk = System.nanoTime();
                double timeParFkMs = (endParFk - startParFk) / 1_000_000.0;
                // Parallel (Executor) timing
                /*long startParEx = System.nanoTime();
                ExecutorServiceProcessing.applyFilter(selectedFilter, parallelExImage, numThreads, blockSize);
                long endParEx = System.nanoTime();
                double timeParExMs = (endParEx - startParEx) / 1_000_000.0;*/
                // Calculate speedup
                double speedupFk = timeSeqMs / timeParFkMs;
                //double speedupEx = timeSeqMs / timeParExMs;
                System.out.printf("%d,%.4f\n", numThreads, speedupFk/* , speedupEx*/);
            }
        } catch (IOException e) {
            System.err.println("❌ Error processing image: " + e.getMessage());
        }

        // VIDEO: scaling experiment for plotting
        /*try {
            String inputVideo = "4k3s.mp4";
            FilterType chosenFilter = FilterType.EDGE_DETECTION;
            ImageProcessor Processor1 = ImageProcessor.SEQUENTIAL;
            ImageProcessor Processor2 = ImageProcessor.FORKJOIN;
            ImageProcessor Processor3 = ImageProcessor.EXECUTOR;
            int width, height;
            try (FFmpegFrameGrabber grabber = new org.bytedeco.javacv.FFmpegFrameGrabber(inputVideo)) {
                grabber.start();
                width = grabber.getImageWidth();
                height = grabber.getImageHeight();
                grabber.stop();
            }
            int maxThreads = Runtime.getRuntime().availableProcessors() * 2;
            System.out.println("threads,speedup_fk,speedup_ex");
            for (int numThreads = 1; numThreads <= maxThreads; numThreads++) {
                var numBlocks = numThreads * 2;
                int imageArea = width * height;
                int blockArea = Math.max(1, imageArea / numBlocks);
                int blockSize = (int) Math.ceil(Math.sqrt(blockArea));
                String outputVideoSeq = "outputSeq.mp4";
                String outputVideoFk = "outputFk.mp4";
                String outputVideoEx = "outputEx.mp4";
                long startSeq = System.currentTimeMillis();
                VideoProcessor.processVideoInMemory(inputVideo, outputVideoSeq, chosenFilter, Processor1, numThreads, blockSize);
                long endSeq = System.currentTimeMillis() - startSeq;
                long startFk = System.currentTimeMillis();
                VideoProcessor.processVideoInMemory(inputVideo, outputVideoFk, chosenFilter, Processor2, numThreads, blockSize);
                long endFk = System.currentTimeMillis() - startFk;
                long startEx = System.currentTimeMillis();
                VideoProcessor.processVideoInMemory(inputVideo, outputVideoEx, chosenFilter, Processor3, numThreads, blockSize);
                long endEx = System.currentTimeMillis() - startEx;
                double totalTimeSeq = (endSeq / 1000.0);
                double totalTimeFk = (endFk / 1000.0);
                double totalTimeEx = (endEx / 1000.0);
                double speedupFk = totalTimeSeq / totalTimeFk;
                double speedupEx = totalTimeSeq / totalTimeEx;
                System.out.printf("%d,%.4f,%.4f\n", numThreads, speedupFk, speedupEx);
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to process video: " + e.getMessage());
        }*/
    }

    // Deep copy utility
    private static BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(bi.getWidth(), bi.getHeight(), bi.getType());
        for (int x = 0; x < bi.getWidth(); x++) {
            for (int y = 0; y < bi.getHeight(); y++) {
                copy.setRGB(x, y, bi.getRGB(x, y));
            }
        }
        return copy;
    }
}
