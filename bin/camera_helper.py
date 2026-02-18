#!/usr/bin/env python3
"""
Camera helper script for Java ChatAPP
Captures frames from camera and outputs as raw RGB24
"""
import cv2
import sys
import time

def main():
    camera_id = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    
    # Try to open camera (prefer AVFoundation on macOS)
    if sys.platform == "darwin":
        cap = cv2.VideoCapture(camera_id, cv2.CAP_AVFOUNDATION)
    else:
        cap = cv2.VideoCapture(camera_id)
    
    if not cap.isOpened():
        print(f"ERROR: Could not open camera {camera_id}", file=sys.stderr)
        sys.exit(1)
    
    # Set resolution to 320x240
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 320)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 240)
    cap.set(cv2.CAP_PROP_FPS, 10)
    
    # Print to stderr that we're ready
    print(f"READY: Camera {camera_id} opened at 320x240", file=sys.stderr)
    sys.stderr.flush()
    
    # Warm up a few frames to stabilize exposure
    for _ in range(5):
        cap.read()
        time.sleep(0.05)

    # Capture frames continuously
    consecutive_failures = 0
    max_failures = 50
    last_warn_at = 0
    while True:
        ret, frame = cap.read()

        if not ret:
            consecutive_failures += 1
            now = time.time()
            if now - last_warn_at > 2:
                print("WARN: Camera read failed", file=sys.stderr)
                sys.stderr.flush()
                last_warn_at = now
            if consecutive_failures >= max_failures:
                print("ERROR: Too many capture failures, stopping camera", file=sys.stderr)
                sys.stderr.flush()
                break
            time.sleep(0.05)
            continue

        consecutive_failures = 0
        
        # Make sure it's the right size
        if frame.shape[:2] != (240, 320):
            frame = cv2.resize(frame, (320, 240))
        
        # Convert BGR to RGB
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        # Write raw RGB24 data to stdout
        sys.stdout.buffer.write(frame_rgb.tobytes())
        sys.stdout.flush()
    
    cap.release()

if __name__ == "__main__":
    main()
