// File: MainGUI.java
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainGUI extends Application {

    private File selectedFile;
    private final ImageView inputPreview = new ImageView();
    private final ImageView outputPreview = new ImageView();
    private final MediaView inputVideoPreview = new MediaView();
    private final MediaView outputVideoPreview = new MediaView();
    private final Label resultLabel = new Label();
    private final DecimalFormat df = new DecimalFormat("#.##");
    private final Hyperlink inputVideoLink = new Hyperlink();
    private final Hyperlink outputVideoLink = new Hyperlink();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("4K Image/Video Processor");

        ComboBox<FilterType> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FilterType.values());
        filterCombo.setValue(FilterType.EDGE_DETECTION);

        ComboBox<ImageProcessor> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll(ImageProcessor.FORKJOIN, ImageProcessor.EXECUTOR);
        methodCombo.setValue(ImageProcessor.FORKJOIN);

        TextField threadInput = new TextField("8");

        Button fileButton = new Button("Choose File");
        Label fileLabel = new Label("No file selected");

        fileButton.setOnAction(_ -> {
            FileChooser chooser = new FileChooser();
            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                selectedFile = file;
                fileLabel.setText(file.getName());
                // Always clear output preview on new upload
                outputPreview.setImage(null);
                outputPreview.setVisible(false);
                outputVideoLink.setVisible(false);
                outputVideoLink.setText("");
                if (file.getName().endsWith(".mp4")) {
                    inputPreview.setImage(null);
                    inputPreview.setVisible(false);
                    inputVideoLink.setText("Open input video: " + file.getName());
                    inputVideoLink.setOnAction(e -> {
                        try { Desktop.getDesktop().open(file); } catch (Exception ex) { ex.printStackTrace(); }
                    });
                    inputVideoLink.setVisible(true);
                } else {
                    inputVideoLink.setVisible(false);
                    inputPreview.setVisible(true);
                    try {
                        BufferedImage img = ImageIO.read(file);
                        inputPreview.setImage(SwingFXUtils.toFXImage(img, null));
                        inputPreview.setFitWidth(350);
                        inputPreview.setPreserveRatio(true);
                        inputPreview.setSmooth(true);
                        inputPreview.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 10, 0, 0, 0);");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Button runButton = new Button("Run");
        runButton.setOnAction(_ -> {
            int threads = Integer.parseInt(threadInput.getText());
            FilterType filter = filterCombo.getValue();
            ImageProcessor method = methodCombo.getValue();

            if (selectedFile == null) {
                resultLabel.setText("❌ No file selected.");
                return;
            }

            try {
                if (selectedFile.getName().endsWith(".mp4")) {
                    String outputPath = "gui_output.mp4";
                    // --- Dynamic block size calculation for video ---
                    int width, height;
                    try (FFmpegFrameGrabber grabber = new org.bytedeco.javacv.FFmpegFrameGrabber(selectedFile.getAbsolutePath())) {
                        grabber.start();
                        width = grabber.getImageWidth();
                        height = grabber.getImageHeight();
                        grabber.stop();
                    }
                    int numBlocks = threads * 2;
                    int imageArea = width * height;
                    int blockArea = Math.max(1, imageArea / numBlocks);
                    int blockSize = (int) Math.ceil(Math.sqrt(blockArea));

                    // --- Memory and CPU tracking for sequential ---
                    long memSeqBefore = getUsedMemory();
                    double cpuSeqBefore = getProcessCpuLoad();
                    long startSeq = System.currentTimeMillis();
                    VideoProcessor.processVideoInMemory(selectedFile.getAbsolutePath(), outputPath, filter, ImageProcessor.SEQUENTIAL, threads, blockSize);
                    long endSeq = System.currentTimeMillis() - startSeq;
                    long memSeqAfter = getUsedMemory();
                    double cpuSeqAfter = getProcessCpuLoad();
                    long seqPeakMem = Math.max(memSeqBefore, memSeqAfter);

                    // --- Memory and CPU tracking for parallel ---
                    long memParBefore = getUsedMemory();
                    double[] cpuSamples = new double[20];
                    Thread cpuSampler = new Thread(() -> {
                        for (int i = 0; i < cpuSamples.length; i++) {
                            cpuSamples[i] = getProcessCpuLoad();
                            try { Thread.sleep((long)(endSeq / (cpuSamples.length + 1))); } catch (InterruptedException ignored) {}
                        }
                    });
                    cpuSampler.start();
                    long startPar = System.currentTimeMillis();
                    VideoProcessor.processVideoInMemory(selectedFile.getAbsolutePath(), outputPath, filter, method, threads, blockSize);
                    long endPar = System.currentTimeMillis() - startPar;
                    long memParAfter = getUsedMemory();
                    try { cpuSampler.join(); } catch (InterruptedException ignored) {}
                    double cpuParMax = 0;
                    for (double v : cpuSamples) cpuParMax = Math.max(cpuParMax, v);
                    long parPeakMem = Math.max(memParBefore, memParAfter);
                    double memRatio = parPeakMem / (double)seqPeakMem;

                    double speedup = (endSeq / 1000.0) / (endPar / 1000.0);

                    long seqOverhead = seqPeakMem - memSeqBefore;
                    long parOverhead = parPeakMem - memParBefore;
                    String memWarn = parOverhead > 2 * seqOverhead ? "⚠️ Parallel memory overhead > 2x sequential!" : "";

                    resultLabel.setText(
                        "✅ Video done\n" +
                        "Speedup: " + df.format(speedup) + "x\n" +
                        "Max CPU during parallel: " + df.format(cpuParMax * 100) + "%\n" +
                        "Seq mem overhead: " + (seqOverhead / (1024 * 1024)) + " MB\n" +
                        "Par mem overhead: " + (parOverhead / (1024 * 1024)) + " MB\n" +
                        memWarn
                    );
                    // Show output video link
                    outputPreview.setImage(null);
                    outputPreview.setVisible(false);
                    outputVideoLink.setText("Open output video: " + outputPath);
                    outputVideoLink.setOnAction(e -> {
                        try { Desktop.getDesktop().open(new File(outputPath)); } catch (Exception ex) { ex.printStackTrace(); }
                    });
                    outputVideoLink.setVisible(true);
                } else {
                    outputVideoLink.setVisible(false);
                    outputPreview.setVisible(true);
                    BufferedImage original = ImageIO.read(selectedFile);
                    int width = original.getWidth();
                    int height = original.getHeight();
                    int numBlocks = threads * 2;
                    int imageArea = width * height;
                    int blockArea = Math.max(1, imageArea / numBlocks);
                    int blockSize = (int) Math.ceil(Math.sqrt(blockArea));
                    BufferedImage copy1 = deepCopy(original);
                    BufferedImage copy2 = deepCopy(original);

                    // --- Memory and CPU tracking for sequential ---
                    long memSeqBefore = getUsedMemory();
                    double cpuSeqBefore = getProcessCpuLoad();
                    long t1s = System.nanoTime();
                    SequentialProcessing.applyFilter(filter, copy1);
                    long t1e = System.nanoTime();
                    long memSeqAfter = getUsedMemory();
                    double cpuSeqAfter = getProcessCpuLoad();
                    long seqPeakMem = Math.max(memSeqBefore, memSeqAfter);

                    // --- Memory and CPU tracking for parallel ---
                    long memParBefore = getUsedMemory();
                    double[] cpuSamples = new double[20];
                    Thread cpuSampler = new Thread(() -> {
                        for (int i = 0; i < cpuSamples.length; i++) {
                            cpuSamples[i] = getProcessCpuLoad();
                            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        }
                    });
                    cpuSampler.start();
                    long t2s;
                    long t2e;
                    if (method == ImageProcessor.FORKJOIN){
                        t2s = System.nanoTime();
                        ForkJoinProcessing.applyFilter(filter, copy2, threads, blockSize);
                        t2e = System.nanoTime();
                    }
                    else{
                        t2s = System.nanoTime();
                        ExecutorServiceProcessing.applyFilter(filter, copy2, threads, blockSize);
                        t2e = System.nanoTime();
                    }
                    long memParAfter = getUsedMemory();
                    try { cpuSampler.join(); } catch (InterruptedException ignored) {}
                    double cpuParMax = 0;
                    for (double v : cpuSamples) cpuParMax = Math.max(cpuParMax, v);
                    long parPeakMem = Math.max(memParBefore, memParAfter);
                    double memRatio = parPeakMem / (double)seqPeakMem;

                    double seqMs = (t1e - t1s) / 1e6;
                    double parMs = (t2e - t2s) / 1e6;
                    double speedup = seqMs / parMs;

                    long seqOverhead = seqPeakMem - memSeqBefore;
                    long parOverhead = parPeakMem - memParBefore;
                    String memWarn = parOverhead > 2 * seqOverhead ? "⚠️ Parallel memory overhead > 2x sequential!" : "";

                    outputPreview.setImage(SwingFXUtils.toFXImage(copy2, null));
                    outputPreview.setFitWidth(350);
                    outputPreview.setPreserveRatio(true);
                    outputPreview.setSmooth(true);
                    outputPreview.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 10, 0, 0, 0);");
                    ImageIO.write(copy2, "jpg", new File("gui_output.jpg"));

                    resultLabel.setText(
                        "✅ Image done\n" +
                        "Speedup: " + df.format(speedup) + "x\n" +
                        "Max CPU during parallel: " + df.format(cpuParMax * 100) + "%\n" +
                        "Seq mem overhead: " + (seqOverhead / (1024 * 1024)) + " MB\n" +
                        "Par mem overhead: " + (parOverhead / (1024 * 1024)) + " MB\n" +
                        memWarn
                    );
                }
            } catch (Exception ex) {
                resultLabel.setText("❌ Error: " + ex.getMessage());
            }
        });

        VBox controls = new VBox(10,
                new Label("🖼️ Filter Type:"), filterCombo,
                new Label("⚙️ Processor Method:"), methodCombo,
                new Label("🔢 Threads:"), threadInput,
                fileButton, fileLabel, runButton, resultLabel);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.TOP_LEFT);

        VBox leftBox = new VBox(10, new Label("📥 Input Preview"), inputPreview, inputVideoLink);
        VBox rightBox = new VBox(10, new Label("📤 Output Preview"), outputPreview, outputVideoLink);
        leftBox.setAlignment(Pos.CENTER);
        rightBox.setAlignment(Pos.CENTER);
        inputVideoLink.setVisible(false);
        outputVideoLink.setVisible(false);

        HBox previews = new HBox(30, leftBox, rightBox);
        previews.setAlignment(Pos.CENTER);
        previews.setPadding(new Insets(10));

        VBox root = new VBox(20, controls, previews);
        root.setPadding(new Insets(20));
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(bi.getWidth(), bi.getHeight(), bi.getType());
        for (int x = 0; x < bi.getWidth(); x++)
            for (int y = 0; y < bi.getHeight(); y++)
                copy.setRGB(x, y, bi.getRGB(x, y));
        return copy;
    }

    private static double getProcessCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sysBean) {
            return sysBean.getProcessCpuLoad();
        }
        return -1;
    }

    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
