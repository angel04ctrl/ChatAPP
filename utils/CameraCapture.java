package utils;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import java.io.*;
import java.util.concurrent.TimeUnit;

public class CameraCapture {
    private Process captureProcess;
    private InputStream frameStream;
    private boolean isOpen = false;
    private volatile byte[] latestFrameBytes;
    private Thread readerThread;
    private volatile long frameCount = 0;
    private volatile long lastFrameAt = 0;

    public CameraCapture(int cameraIndex) {
        try {
            startPythonCapture(cameraIndex);
        } catch (Exception e) {
            System.out.println("Camera: " + e.getMessage());
        }
    }

    private void startPythonCapture(int cameraIndex) {
        try {
            String scriptPath = System.getProperty("user.dir") + "/camera_helper.py";
            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                System.out.println("Camera startup error: camera_helper.py not found");
                return;
            }

            String pythonPath = resolvePythonExecutable();
            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath, String.valueOf(cameraIndex));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.environment().put("PYTHONUNBUFFERED", "1");

            this.captureProcess = pb.start();
            this.frameStream = captureProcess.getInputStream();

            Thread.sleep(1000);

            if (captureProcess.isAlive()) {
                this.isOpen = true;
                startReaderThread();
                System.out.println("✓ Camera started successfully via Python OpenCV");
            } else {
                System.out.println("Camera: Python process failed to start");
            }
        } catch (Exception e) {
            System.out.println("Camera startup error: " + e.getMessage());
        }
    }

    private String resolvePythonExecutable() {
        String venvPython = System.getProperty("user.dir") + "/.venv/bin/python";
        File venvFile = new File(venvPython);
        if (venvFile.exists() && venvFile.canExecute()) {
            return venvPython;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (p.waitFor(1, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "python3";
            }
        } catch (Exception ignored) {
        }

        return "python";
    }

    public Image captureFrame() {
        byte[] frameData = latestFrameBytes;
        if (frameData == null || frameData.length != 320 * 240 * 3) {
            return null;
        }

        WritableImage image = new WritableImage(320, 240);
        int[] pixels = new int[320 * 240];

        for (int i = 0; i < pixels.length; i++) {
            int r = frameData[i * 3] & 0xFF;
            int g = frameData[i * 3 + 1] & 0xFF;
            int b = frameData[i * 3 + 2] & 0xFF;
            pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }

        image.getPixelWriter().setPixels(0, 0, 320, 240,
                PixelFormat.getIntArgbInstance(), pixels, 0, 320);
        return image;
    }

    private void startReaderThread() {
        readerThread = new Thread(() -> {
            if (frameStream == null) {
                return;
            }

            try {
                while (isOpen && captureProcess != null && captureProcess.isAlive()) {
                    byte[] frameData = new byte[320 * 240 * 3];
                    int totalRead = 0;

                    while (totalRead < frameData.length) {
                        int bytesRead = frameStream.read(frameData, totalRead, frameData.length - totalRead);
                        if (bytesRead == -1) {
                            isOpen = false;
                            return;
                        }
                        totalRead += bytesRead;
                    }

                    latestFrameBytes = frameData;
                    frameCount++;
                    lastFrameAt = System.currentTimeMillis();
                }
            } catch (IOException ignored) {
                isOpen = false;
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void close() {
        if (captureProcess != null) {
            try {
                captureProcess.destroy();
                if (frameStream != null) {
                    frameStream.close();
                }
                if (!captureProcess.waitFor(2, TimeUnit.SECONDS)) {
                    captureProcess.destroyForcibly();
                }
                isOpen = false;
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public boolean isOpened() {
        return isOpen && captureProcess != null && captureProcess.isAlive();
    }

    public long getFrameCount() {
        return frameCount;
    }

    public long getLastFrameAt() {
        return lastFrameAt;
    }
}
