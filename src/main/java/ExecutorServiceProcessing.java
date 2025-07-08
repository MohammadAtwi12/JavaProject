import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceProcessing {


    public static void applyFilter(FilterType filter, BufferedImage image, int numThreads, int blockSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        int BLOCK_SIZE = blockSize;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Submit tasks for non-overlapping blocks
        for (int y = 0; y < height; y += BLOCK_SIZE) {
            for (int x = 0; x < width; x += BLOCK_SIZE) {
                final int blockX = x;
                final int blockY = y;
                final int blockWidth = Math.min(BLOCK_SIZE, width - blockX);
                final int blockHeight = Math.min(BLOCK_SIZE, height - blockY);

                executor.execute(() -> {
                    switch (filter) {
                        case GRAYSCALE -> Filters.applyGrayscale(image, blockX, blockY, blockWidth, blockHeight);
                        case GAUSSIAN_BLUR -> Filters.applyGaussianBlur(image, blockX, blockY, blockWidth, blockHeight);
                        case INVERT -> Filters.applyInvert(image, blockX, blockY, blockWidth, blockHeight);
                        case SEPIA -> Filters.applySepia(image, blockX, blockY, blockWidth, blockHeight);
                        case EDGE_DETECTION -> Filters.applyEdgeDetection(image, blockX, blockY, blockWidth, blockHeight);
                    }
                });
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                System.err.println("❌ ExecutorService: Filter application timed out");
            }
        } catch (InterruptedException e) {
        }
    }
}
