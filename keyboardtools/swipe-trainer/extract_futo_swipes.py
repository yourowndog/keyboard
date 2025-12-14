#!/usr/bin/env python3
"""
Extract real human swipe samples from FUTO dataset for the top N words.
Stores 10 diverse swipes per word in binary format for fast loading.
"""
import pyarrow.parquet as pq
import struct
import os
import random
import numpy as np

def normalize_swipe(points, canvas_width, canvas_height):
    """
    Normalize swipe points to 0-1 coordinate space.
    Input: list of dicts with 'x', 'y', 't' keys
    Output: list of (x_norm, y_norm) tuples
    """
    if points is None or len(points) == 0:
        return []
    
    normalized = []
    for point in points:
        # Normalize to 0-1 space
        x_norm = point['x'] / canvas_width
        y_norm = point['y'] / canvas_height
        normalized.append((x_norm, y_norm))
    
    return normalized

def resample_swipe(points, num_points=50):
    """
    Resample a swipe to exactly num_points using linear interpolation.
    This ensures consistent comparison between swipes.
    """
    if len(points) < 2:
        return points * num_points if points else []
    
    # Calculate cumulative distance along path
    distances = [0.0]
    for i in range(1, len(points)):
        dx = points[i][0] - points[i-1][0]
        dy = points[i][1] - points[i-1][1]
        dist = np.sqrt(dx*dx + dy*dy)
        distances.append(distances[-1] + dist)
    
    total_dist = distances[-1]
    if total_dist == 0:
        return [points[0]] * num_points
    
    # Sample at even intervals
    resampled = []
    target_distances = [i * total_dist / (num_points - 1) for i in range(num_points)]
    
    current_idx = 0
    for target_dist in target_distances:
        # Find segment containing target distance
        while current_idx < len(distances) - 1 and distances[current_idx + 1] < target_dist:
            current_idx += 1
        
        if current_idx >= len(distances) - 1:
            resampled.append(points[-1])
        else:
            # Linear interpolation between points
            segment_start_dist = distances[current_idx]
            segment_end_dist = distances[current_idx + 1]
            segment_length = segment_end_dist - segment_start_dist
            
            if segment_length == 0:
                resampled.append(points[current_idx])
            else:
                t = (target_dist - segment_start_dist) / segment_length
                x = points[current_idx][0] * (1 - t) + points[current_idx + 1][0] * t
                y = points[current_idx][1] * (1 - t) + points[current_idx + 1][1] * t
                resampled.append((x, y))
    
    return resampled

def select_diverse_swipes(swipes, num_samples=10):
    """
    Select diverse swipes from available samples.
    Strategy: Pick swipes with varying path lengths and shapes.
    """
    if len(swipes) <= num_samples:
        return swipes
    
    # Calculate path length for each swipe
    swipe_lengths = []
    for swipe in swipes:
        total_len = 0
        for i in range(1, len(swipe)):
            dx = swipe[i][0] - swipe[i-1][0]
            dy = swipe[i][1] - swipe[i-1][1]
            total_len += np.sqrt(dx*dx + dy*dy)
        swipe_lengths.append((total_len, swipe))
    
    # Sort by length
    swipe_lengths.sort(key=lambda x: x[0])
    
    # Pick evenly distributed samples across the length spectrum
    selected = []
    step = len(swipe_lengths) / num_samples
    for i in range(num_samples):
        idx = int(i * step)
        selected.append(swipe_lengths[idx][1])
    
    return selected

def extract_futo_swipes(parquet_path, output_path, top_n_words=1000, swipes_per_word=10):
    """
    Extract real swipe samples from FUTO dataset.
    
    Format:
    - Header: [num_words: u32][num_swipes_per_word: u32][points_per_swipe: u32]
    - For each word:
      - [word_length: u16][word_utf8_bytes]
      - [num_swipes: u8] (actual count, may be less than swipes_per_word)
      - For each swipe:
        - [flat_coords: points_per_swipe * 2 floats]
    """
    print(f"Reading FUTO swipe data from {parquet_path}...")
    
    # Read parquet file
    parquet_file = pq.ParquetFile(parquet_path)
    
    # Count words across all row groups
    print("Counting word frequencies...")
    word_swipes = {}  # word -> list of normalized swipes
    
    for i in range(parquet_file.metadata.num_row_groups):
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        
        print(f"Processing row group {i+1}/{parquet_file.metadata.num_row_groups}...")
        
        for _, row in df.iterrows():
            word = row['word']
            points = row['data']
            canvas_width = row['canvas_width']
            canvas_height = row['canvas_height']
            
            # Normalize swipe
            normalized = normalize_swipe(points, canvas_width, canvas_height)
            if not normalized:
                continue
            
            # Resample to consistent point count
            resampled = resample_swipe(normalized, num_points=50)
            
            if word not in word_swipes:
                word_swipes[word] = []
            word_swipes[word].append(resampled)
    
    print(f"\nFound {len(word_swipes)} unique words")
    
    # Sort by frequency (number of samples)
    word_counts = [(word, len(swipes)) for word, swipes in word_swipes.items()]
    word_counts.sort(key=lambda x: x[1], reverse=True)
    
    print(f"\nTop 20 words by sample count:")
    for i, (word, count) in enumerate(word_counts[:20], 1):
        print(f"{i:2d}. '{word}': {count:,} samples")
    
    # Take top N words
    top_words = word_counts[:top_n_words]
    print(f"\nExtracting top {top_n_words} words...")
    
    # Write binary format
    print(f"Writing to {output_path}...")
    with open(output_path, 'wb') as f:
        # Header
        f.write(struct.pack('III', len(top_words), swipes_per_word, 50))  # 50 points per swipe
        
        for word, count in top_words:
            # Select diverse swipes
            swipes = word_swipes[word]
            selected_swipes = select_diverse_swipes(swipes, swipes_per_word)
            
            # Write word
            word_bytes = word.encode('utf-8')
            f.write(struct.pack('H', len(word_bytes)))
            f.write(word_bytes)
            
            # Write number of swipes
            f.write(struct.pack('B', len(selected_swipes)))
            
            # Write each swipe
            for swipe in selected_swipes:
                # Flatten to [x1, y1, x2, y2, ...]
                flat = [coord for point in swipe for coord in point]
                f.write(struct.pack(f'{len(flat)}f', *flat))
    
    # Stats
    file_size = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n=== DONE ===")
    print(f"Words extracted: {len(top_words)}")
    print(f"Swipes per word: {swipes_per_word}")
    print(f"Points per swipe: 50")
    print(f"File size: {file_size:.2f} MB")
    print(f"Ready for Android!")

def main():
    parquet_path = 'futo_swipes.parquet'
    output_path = '/home/sam/projects/keyboard/app/src/main/assets/ime/swipe/futo_swipes.bin'
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    extract_futo_swipes(
        parquet_path=parquet_path,
        output_path=output_path,
        top_n_words=1000,
        swipes_per_word=10
    )

if __name__ == '__main__':
    main()
