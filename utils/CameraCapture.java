package utils;

import com.github.sarxos.webcam.Webcam;
import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

public class CameraCapture {
    private Webcam webcam;
    private boolean isOpen = false;

    public CameraCapture(int cameraIndex) {
        try {
            // Get default webcam or specified by index
            if (cameraIndex == 0) {
                this.webcam = Webcam.getDefault();
            } else {
                Webcam.getWebcams();
                // For simplicity, get default
                this.webcam = Webcam.getDefault();
            }

            if (webcam != null) {
                // Set size
                webcam.setViewSize(new java.awt.Dimension(320, 240));
                // Open with timeout
                this.isOpen = webcam.open();
                if (isOpen) {
                    System.out.println("Camera opened successfully");
                } else {
                    System.err.println("Failed to open camera - timeout");
                }
            } else {
                System.err.println("No camera found on system");
            }
        } catch (Exception e) {
            System.err.println("Error initializing camera: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Image captureFrame() {
        if (!isOpen || webcam == null) {
            return null;
        }

        try {
            if (!webcam.isOpen()) {
                return null;
            }

            BufferedImage bufferedImage = webcam.getImage();
            if (bufferedImage == null) {
                return null;
            }

            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (Exception e) {
            System.err.println("Error capturing frame: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        if (webcam != null && isOpen) {
            try {
                webcam.close();
                isOpen = false;
                System.out.println("Camera closed");
            } catch (Exception e) {
                System.err.println("Error closing camera: " + e.getMessage());
            }
        }
    }

    public boolean isOpened() {
        return isOpen && webcam != null && webcam.isOpen();
    }
}

