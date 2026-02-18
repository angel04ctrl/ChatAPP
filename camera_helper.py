#!/usr/bin/env python3
"""
Camera helper script for Java ChatAPP
Captures frames from camera and outputs as raw RGB24
"""
import cv2
import sys
import struct

def main():
    camera_id = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    
    # Try to open camera
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
    
    # Capture frames continuously
    while True:
        ret, frame = cap.read()
        
        if not ret:
            break
        
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
