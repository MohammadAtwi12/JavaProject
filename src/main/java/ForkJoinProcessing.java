import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class ForkJoinProcessing {

    public static void applyFilter(FilterType filter, BufferedImage image, int numThreads) {
        int imageArea = image.getWidth() * image.getHeight();
        int blockArea = imageArea / numThreads;
        int blockSize = (int) Math.ceil(Math.sqrt(blockArea));

        try (ForkJoinPool pool = new ForkJoinPool(numThreads)) {
            pool.invoke(new FilterTask(filter, image, 0, 0, image.getWidth(), image.getHeight(), blockSize));
        }
    }

    public static void applyFilter(FilterType filter, BufferedImage image, int numThreads, int blockSize) {
        int BLOCK_SIZE = blockSize;

        try (ForkJoinPool pool = new ForkJoinPool(numThreads)) {
            pool.invoke(new FilterTask(filter, image, 0, 0, image.getWidth(), image.getHeight(), BLOCK_SIZE));
        }
    }

    private static class FilterTask extends RecursiveAction  {

        private final int BLOCK_SIZE;
        private final FilterType filter;
        private final BufferedImage image;
        private final int x, y, width, height;

        public FilterTask(FilterType filter, BufferedImage image, int x, int y, int width, int height, int blockSize) {
            this.filter = filter;
            this.image = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.BLOCK_SIZE = blockSize;
        }

        @Override
        protected void compute() {
            if (width <= BLOCK_SIZE && height <= BLOCK_SIZE) {
                // Directly apply filter to this block
                switch (filter) {
                    case GAUSSIAN_BLUR -> Filters.applyGaussianBlur(image, x, y, width, height);
                    case GRAYSCALE -> Filters.applyGrayscale(image, x, y, width, height);
                    case INVERT -> Filters.applyInvert(image, x, y, width, height);
                    case SEPIA -> Filters.applySepia(image, x, y, width, height);
                    case EDGE_DETECTION -> Filters.applyEdgeDetection(image, x, y, width, height);
                }
            } else {
                int wMid = width / 2;
                int hMid = height / 2;

                invokeAll(
                    new FilterTask(filter, image, x, y, wMid, hMid, BLOCK_SIZE),
                    new FilterTask(filter, image, x + wMid, y, width - wMid, hMid, BLOCK_SIZE),
                    new FilterTask(filter, image, x, y + hMid, wMid, height - hMid, BLOCK_SIZE),
                    new FilterTask(filter, image, x + wMid, y + hMid, width - wMid, height - hMid, BLOCK_SIZE)
                );
            }
        }
    }
}
