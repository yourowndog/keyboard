#!/usr/bin/env python3
"""
Precompute ideal swipe gestures for dictionary words.
Generates normalized (0-1) coordinate paths that can be scaled to any keyboard size.
"""
import json
import math
from typing import List, Tuple, Dict

# Standard QWERTY layout with normalized positions (0-1 coordinates)
# Based on typical keyboard proportions: ~10 columns, 3 rows
QWERTY_LAYOUT = {
    # Top row (numbers not included - swipe doesn't need them)
    
    # First alpha row (QWERTYUIOP)
    'q': (0.05, 0.167), 'w': (0.15, 0.167), 'e': (0.25, 0.167), 'r': (0.35, 0.167),
    't': (0.45, 0.167), 'y': (0.55, 0.167), 'u': (0.65, 0.167), 'i': (0.75, 0.167),
    'o': (0.85, 0.167), 'p': (0.95, 0.167),
    
    # Second alpha row (ASDFGHJKL) - slightly offset
    'a': (0.075, 0.50), 's': (0.175, 0.50), 'd': (0.275, 0.50), 'f': (0.375, 0.50),
    'g': (0.475, 0.50), 'h': (0.575, 0.50), 'j': (0.675, 0.50), 'k': (0.775, 0.50),
    'l': (0.875, 0.50),
    
    # Third alpha row (ZXCVBNM) - more offset
    'z': (0.15, 0.833), 'x': (0.25, 0.833), 'c': (0.35, 0.833), 'v': (0.45, 0.833),
    'b': (0.55, 0.833), 'n': (0.65, 0.833), 'm': (0.75, 0.833),
}

def distance(p1: Tuple[float, float], p2: Tuple[float, float]) -> float:
    """Calculate Euclidean distance between two points."""
    return math.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

def resample_path(points: List[Tuple[float, float]], num_points: int) -> List[Tuple[float, float]]:
    """
    Resample a path to have exactly num_points evenly spaced points.
    This matches the Kotlin resampling logic.
    """
    if len(points) < 2:
        # Edge case: if only one point, just repeat it
        return [points[0]] * num_points if points else []
    
    # Calculate total path length
    total_length = 0.0
    for i in range(len(points) - 1):
        total_length += distance(points[i], points[i + 1])
    
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
    """
    Generate ideal swipe gesture for a word.
    Matches the Kotlin generateIdealGestures logic.
    
    Args:
        word: The word to generate gesture for
        with_loops: If True, add loops for duplicate letters
    
    Returns:
        List of (x, y) coordinate tuples in normalized 0-1 space
    """
    word_lower = word.lower()
    points = []
    prev_char = None
    
    for char in word_lower:
        if char not in QWERTY_LAYOUT:
            # Skip characters not on QWERTY (e.g., punctuation)
            continue
        
        x, y = QWERTY_LAYOUT[char]
        
        # Handle duplicate letters with loops (like "pool" vs "poll")
        if with_loops and char == prev_char and points:
            # Add a small loop: bottom-right, top-right, top-left, bottom-left
            loop_size = 0.015  # Small loop in normalized space
            points.append((x + loop_size, y + loop_size))  # bottom-right
            points.append((x + loop_size, y - loop_size))  # top-right
            points.append((x - loop_size, y - loop_size))  # top-left
            points.append((x - loop_size, y + loop_size))  # bottom-left
        
        points.append((x, y))
        prev_char = char
    
    return points

def precompute_gestures(words: List[str], num_sample_points: int = 50) -> Dict[str, List[List[float]]]:
    """
    Precompute ideal gestures for all words.
    
    Returns:
        Dictionary mapping word -> list of gesture paths
        Each path is a flat list of coordinates: [x1, y1, x2, y2, ...]
    """
    gesture_data = {}
    
    for i, word in enumerate(words):
        if i % 1000 == 0:
            print(f"Processing word {i+1}/{len(words)}: {word}")
        
        # Generate base gesture
        base_points = generate_ideal_gesture(word, with_loops=False)
        if not base_points:
            continue
        
        # Check if word has duplicate letters
        has_duplicates = len(word) != len(set(word.lower()))
        
        gestures = []
        
        # Always include base gesture
        resampled = resample_path(base_points, num_sample_points)
        # Flatten to [x1, y1, x2, y2, ...] for compact JSON
        flat = [coord for point in resampled for coord in point]
        gestures.append(flat)
        
        # Add loop variant if word has duplicate letters
        if has_duplicates:
            loop_points = generate_ideal_gesture(word, with_loops=True)
            resampled_loop = resample_path(loop_points, num_sample_points)
            flat_loop = [coord for point in resampled_loop for coord in point]
            gestures.append(flat_loop)
        
        gesture_data[word] = gestures
    
    return gesture_data

def main():
    print("=== Precomputing Ideal Swipe Gestures ===\n")
    
    # Load dictionary words from your NLP system
    # We'll read from the bigram file since it has word frequencies
    print("Loading dictionary words...")
    
    # Try to find dictionary files
    import os
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
                        # Filter: only include words with QWERTY letters
                        if word and all(c.lower() in QWERTY_LAYOUT or not c.isalpha() for c in word):
                            words.append(word)
            break
    
    if not words:
        print("ERROR: Could not find dictionary file!")
        return
    
    # Limit to top 10,000 most common words (they're already sorted by frequency)
    words = words[:10000]
    print(f"Loaded {len(words)} words\n")
    
    # Precompute gestures
    print("Generating ideal gestures...")
    gesture_data = precompute_gestures(words, num_sample_points=50)
    
    # Save to JSON
    output_path = '/home/sam/projects/keyboard/app/src/main/assets/ime/swipe/precomputed_gestures.json'
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    print(f"\nSaving to {output_path}...")
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(gesture_data, f, separators=(',', ':'))  # Compact JSON
    
    # Print stats
    file_size = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n=== DONE ===")
    print(f"Words processed: {len(gesture_data)}")
    print(f"File size: {file_size:.2f} MB")
    print(f"Sample points per gesture: 50")
    print(f"Ready to use in Android app!")

if __name__ == '__main__':
    main()
