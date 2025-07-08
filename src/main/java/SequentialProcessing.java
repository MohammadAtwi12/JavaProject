import java.awt.image.BufferedImage;

public class SequentialProcessing {

    public static void applyFilter(FilterType filter, BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        switch (filter) {
            case GRAYSCALE -> Filters.applyGrayscale(img, 0, 0, width, height);
            case GAUSSIAN_BLUR -> Filters.applyGaussianBlur(img, 0, 0, width, height);
            case INVERT -> Filters.applyInvert(img, 0, 0, width, height);
            case SEPIA -> Filters.applySepia(img, 0, 0, width, height);
            case EDGE_DETECTION -> Filters.applyEdgeDetection(img, 0, 0, width, height);
        }
    }
}
