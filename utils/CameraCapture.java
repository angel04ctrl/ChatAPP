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
    private static final String FFMPEG_PATH = "/opt/homebrew/bin/ffmpeg";

    public CameraCapture(int cameraIndex) {
        try {
            // Verify ffmpeg exists
            if (!new File(FFMPEG_PATH).exists()) {
                System.err.println("Warning: ffmpeg not found at " + FFMPEG_PATH);
                isOpen = false;
                return;
            }

            startFFmpegCapture(cameraIndex);
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize camera: " + e.getMessage());
            isOpen = false;
        }
    }

    private void startFFmpegCapture(int cameraIndex) {
        try {
            // Use the specific camera device
            // On macOS with AVFoundation, format is avfoundation
            ProcessBuilder pb = new ProcessBuilder(
                FFMPEG_PATH,
                "-f", "avfoundation",
                "-i", cameraIndex + ":none",  // video:audio (none means no audio)
                "-vf", "scale=320:240",
                "-f", "rawvideo",
                "-pix_fmt", "rgb24",
                "-framerate", "10",
                "-"
            );
            
            // Redirect stderr to suppress messages
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            this.captureProcess = pb.start();
            
            // Give it a moment to initialize
            Thread.sleep(500);
            
            if (captureProcess.isAlive()) {
                this.frameStream = captureProcess.getInputStream();
                this.isOpen = true;
                System.out.println("Camera initialized via ffmpeg (device " + cameraIndex + ")");
            } else {
                // Process died immediately - permission denied or camera access issue
                System.err.println("Warning: ffmpeg process failed to start - camera permissions needed");
                this.isOpen = false;
            }
        } catch (InterruptedException e) {
            System.err.println("Warning: Interrupted while initializing camera: " + e.getMessage());
            this.isOpen = false;
        } catch (Exception e) {
            System.err.println("Warning: Error starting ffmpeg: " + e.getMessage());
            this.isOpen = false;
        }
    }

    public Image captureFrame() {
        if (!isOpen || frameStream == null || captureProcess == null) {
            return null;
        }

        try {
            if (!captureProcess.isAlive()) {
                System.err.println("Warning: ffmpeg process died");
                isOpen = false;
                return null;
            }

            // Read one frame (320x240 RGB24 = 320*240*3 bytes = 230400 bytes)
            byte[] frameData = new byte[320 * 240 * 3];
            int bytesRead = 0;
            int totalRead = 0;
            
            while (totalRead < frameData.length) {
                bytesRead = frameStream.read(frameData, totalRead, frameData.length - totalRead);
                if (bytesRead == -1) {
                    System.err.println("Warning: EOF reached from ffmpeg");
                    isOpen = false;
                    return null;
                }
                totalRead += bytesRead;
            }

            // Convert raw RGB to WritableImage
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
        } catch (IOException e) {
            if (isOpen) {
                System.err.println("Error reading frame: " + e.getMessage());
            }
            return null;
        }
    }

    public void close() {
        if (captureProcess != null) {
            try {
                captureProcess.destroy();
                if (frameStream != null) {
                    frameStream.close();
                }
                
                // Wait for process to die
                if (!captureProcess.waitFor(2, TimeUnit.SECONDS)) {
                    captureProcess.destroyForcibly();
                }
                
                isOpen = false;
                System.out.println("Camera closed");
            } catch (Exception e) {
                System.err.println("Error closing camera: " + e.getMessage());
            }
        }
    }

    public boolean isOpened() {
        return isOpen && captureProcess != null && captureProcess.isAlive();
    }
}



