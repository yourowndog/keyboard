#!/usr/bin/env python3
"""
Precompute ideal swipe gestures and save in binary format for fast loading.
Binary format is much faster to load than JSON (milliseconds vs seconds).
"""
import struct
import math
from typing import List, Tuple, Dict
import os

# Standard QWERTY layout with normalized positions (0-1 coordinates)
QWERTY_LAYOUT = {
    # First alpha row (QWERTYUIOP)
    'q': (0.05, 0.167), 'w': (0.15, 0.167), 'e': (0.25, 0.167), 'r': (0.35, 0.167),
    't': (0.45, 0.167), 'y': (0.55, 0.167), 'u': (0.65, 0.167), 'i': (0.75, 0.167),
    'o': (0.85, 0.167), 'p': (0.95, 0.167),
    
    # Second alpha row (ASDFGHJKL)
    'a': (0.075, 0.50), 's': (0.175, 0.50), 'd': (0.275, 0.50), 'f': (0.375, 0.50),
    'g': (0.475, 0.50), 'h': (0.575, 0.50), 'j': (0.675, 0.50), 'k': (0.775, 0.50),
    'l': (0.875, 0.50),
    
    # Third alpha row (ZXCVBNM)
    'z': (0.15, 0.833), 'x': (0.25, 0.833), 'c': (0.35, 0.833), 'v': (0.45, 0.833),
    'b': (0.55, 0.833), 'n': (0.65, 0.833), 'm': (0.75, 0.833),
}

def distance(p1: Tuple[float, float], p2: Tuple[float, float]) -> float:
    return math.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

def resample_path(points: List[Tuple[float, float]], num_points: int) -> List[Tuple[float, float]]:
    if len(points) < 2:
        return [points[0]] * num_points if points else []
    
    total_length = sum(distance(points[i], points[i + 1]) for i in range(len(points) - 1))
    if total_length == 0:
        return [points[0]] * num_points
    
    interpoint_distance = total_length / num_points
    resampled = [points[0]]
    last_point = points[0]
    cumulative_error = 0.0
    
    for i in range(len(points) - 1):
        dx = points[i + 1][0] - points[i][0]
        dy = points[i + 1][1] - points[i][1]
        norm = math.sqrt(dx**2 + dy**2)
        
        if norm == 0:
            continue
            
        dx /= norm
        dy /= norm
        
        num_new_points = norm / interpoint_distance
        cumulative_error += num_new_points - int(num_new_points)
        
        if cumulative_error > 1:
            num_new_points = int(num_new_points) + int(cumulative_error)
            cumulative_error %= 1
        
        for j in range(int(num_new_points)):
            new_x = last_point[0] + dx * interpoint_distance
            new_y = last_point[1] + dy * interpoint_distance
            last_point = (new_x, new_y)
            resampled.append(last_point)
    
    return resampled

def generate_ideal_gesture(word: str, with_loops: bool = False) -> List[Tuple[float, float]]:
    word_lower = word.lower()
    points = []
    prev_char = None
    
    for char in word_lower:
        if char not in QWERTY_LAYOUT:
            continue
        
        x, y = QWERTY_LAYOUT[char]
        
        if with_loops and char == prev_char and points:
            loop_size = 0.015
            points.append((x + loop_size, y + loop_size))
            points.append((x + loop_size, y - loop_size))
            points.append((x - loop_size, y - loop_size))
            points.append((x - loop_size, y + loop_size))
        
        points.append((x, y))
        prev_char = char
    
    return points

def write_binary_gestures(words: List[str], output_path: str, num_sample_points: int = 50):
    """
    Write gestures in binary format for fast loading.
    
    Format:
    - Header: [num_words: u32][num_sample_points: u32]
    - For each word:
      - [word_length: u16][word_utf8_bytes]
      - [num_gestures: u8]
      - For each gesture:
        - [flat_coords: num_sample_points * 2 floats (x,y pairs)]
    """
    print(f"Writing binary format to {output_path}...")
    
    with open(output_path, 'wb') as f:
        # Header
        f.write(struct.pack('II', len(words), num_sample_points))
        
        written_count = 0
        for i, word in enumerate(words):
            if i % 1000 == 0:
                print(f"Processing {i+1}/{len(words)}: {word}")
            
            # Generate gestures
            base_points = generate_ideal_gesture(word, with_loops=False)
            if not base_points:
                continue
            
            has_duplicates = len(word) != len(set(word.lower()))
            
            gestures = []
            
            # Base gesture
            resampled = resample_path(base_points, num_sample_points)
            flat = [coord for point in resampled for coord in point]
            gestures.append(flat)
            
            # Loop variant if needed
            if has_duplicates:
                loop_points = generate_ideal_gesture(word, with_loops=True)
                resampled_loop = resample_path(loop_points, num_sample_points)
                flat_loop = [coord for point in resampled_loop for coord in point]
                gestures.append(flat_loop)
            
            # Write word
            word_bytes = word.encode('utf-8')
            f.write(struct.pack('H', len(word_bytes)))
            f.write(word_bytes)
            
            # Write number of gestures
            f.write(struct.pack('B', len(gestures)))
            
            # Write each gesture
            for gesture in gestures:
                # Pack all floats for this gesture
                f.write(struct.pack(f'{len(gesture)}f', *gesture))
            
            written_count += 1
        
        print(f"Wrote {written_count} words")

def main():
    print("=== Precomputing Ideal Swipe Gestures (Binary Format) ===\n")
    
    # Load dictionary
    dict_paths = [
        '/home/sam/projects/keyboard/app/src/main/assets/ime/dict/unified_dictionary.tsv',
        '/home/sam/projects/keyboard/app/src/main/assets/ime/dict/aosp_unigram.tsv',
    ]
    
    words = []
    for path in dict_paths:
        if os.path.exists(path):
            print(f"Reading from {path}")
            with open(path, 'r', encoding='utf-8') as f:
                for line in f:
                    parts = line.strip().split('\t')
                    if parts:
                        word = parts[0]
                        if word and all(c.lower() in QWERTY_LAYOUT or not c.isalpha() for c in word):
                            words.append(word)
            break
    
    if not words:
        print("ERROR: Could not find dictionary file!")
        return
    
    # Limit to top 10,000
    words = words[:10000]
    print(f"Loaded {len(words)} words\n")
    
    # Write binary format
    output_path = '/home/sam/projects/keyboard/app/src/main/assets/ime/swipe/precomputed_gestures.bin'
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    write_binary_gestures(words, output_path, num_sample_points=50)
    
    # Stats
    file_size = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n=== DONE ===")
    print(f"File size: {file_size:.2f} MB")
    print(f"Binary format - should load in <100ms!")

if __name__ == '__main__':
    main()
