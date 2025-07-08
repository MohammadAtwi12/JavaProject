import java.awt.Color;
import java.awt.image.BufferedImage;

public class Filters {

    // 1. Grayscale Filter
    public static void applyGrayscale(BufferedImage img, int startX, int startY, int width, int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                Color c = new Color(img.getRGB(x, y));
                int gray = (int) (0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue());
                Color newColor = new Color(gray, gray, gray);
                img.setRGB(x, y, newColor.getRGB());
            }
        }
    }

    // 2. Gaussian Blur Filter (3x3 kernel)
    public static void applyGaussianBlur(BufferedImage img, int startX, int startY, int width, int height) {
        float[][] kernel = {
                { 1f / 16, 2f / 16, 1f / 16 },
                { 2f / 16, 4f / 16, 2f / 16 },
                { 1f / 16, 2f / 16, 1f / 16 }
        };

        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());

        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                float r = 0, g = 0, b = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int px = clamp(x + kx, 0, img.getWidth() - 1);
                        int py = clamp(y + ky, 0, img.getHeight() - 1);
                        Color color = new Color(img.getRGB(px, py));
                        float weight = kernel[ky + 1][kx + 1];

                        r += color.getRed() * weight;
                        g += color.getGreen() * weight;
                        b += color.getBlue() * weight;
                    }
                }

                Color blurred = new Color(clamp((int) r, 0, 255), clamp((int) g, 0, 255), clamp((int) b, 0, 255));
                copy.setRGB(x, y, blurred.getRGB());
            }
        }

        // Copy blurred result back to original image region
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                img.setRGB(x, y, copy.getRGB(x, y));
            }
        }
    }

    // 3. Invert Filter
    public static void applyInvert(BufferedImage img, int startX, int startY, int width, int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                Color c = new Color(img.getRGB(x, y));
                Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
                img.setRGB(x, y, inverted.getRGB());
            }
        }
    }

    // 4. Sepia Filter
    public static void applySepia(BufferedImage img, int startX, int startY, int width, int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                Color c = new Color(img.getRGB(x, y));

                int tr = (int) (0.393 * c.getRed() + 0.769 * c.getGreen() + 0.189 * c.getBlue());
                int tg = (int) (0.349 * c.getRed() + 0.686 * c.getGreen() + 0.168 * c.getBlue());
                int tb = (int) (0.272 * c.getRed() + 0.534 * c.getGreen() + 0.131 * c.getBlue());

                Color sepia = new Color(clamp(tr, 0, 255), clamp(tg, 0, 255), clamp(tb, 0, 255));
                img.setRGB(x, y, sepia.getRGB());
            }
        }
    }

    public static void applyEdgeDetection(BufferedImage img, int startX, int startY, int width, int height) {
        int[][] sobelX = {
                { -1, 0, 1 },
                { -2, 0, 2 },
                { -1, 0, 1 }
        };

        int[][] sobelY = {
                { -1, -2, -1 },
                { 0, 0, 0 },
                { 1, 2, 1 }
        };

        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = startY + 1; y < startY + height - 1 && y < img.getHeight() - 1; y++) {
            for (int x = startX + 1; x < startX + width - 1 && x < img.getWidth() - 1; x++) {
                int gx = 0, gy = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int px = clamp(x + kx, 0, img.getWidth() - 1);
                        int py = clamp(y + ky, 0, img.getHeight() - 1);
                        int gray = getGray(img.getRGB(px, py));

                        gx += sobelX[ky + 1][kx + 1] * gray;
                        gy += sobelY[ky + 1][kx + 1] * gray;
                    }
                }

                int magnitude = clamp((int) Math.sqrt(gx * gx + gy * gy), 0, 255);
                Color edgeColor = new Color(magnitude, magnitude, magnitude);
                copy.setRGB(x, y, edgeColor.getRGB());
            }
        }

        // Copy edge-detected result back to original image region
        for (int y = startY + 1; y < startY + height - 1 && y < img.getHeight() - 1; y++) {
            for (int x = startX + 1; x < startX + width - 1 && x < img.getWidth() - 1; x++) {
                img.setRGB(x, y, copy.getRGB(x, y));
            }
        }
    }

    // Helper to get grayscale value from RGB int
    private static int getGray(int rgb) {
        Color c = new Color(rgb);
        return (int)(0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue());
    }

    // Helper to clamp values to the 0-255 range
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
