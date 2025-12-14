#!/usr/bin/env python3
"""
Physics-Based Synthetic Swipe Generator

Generates realistic synthetic swipe data using biomechanical principles:
- Minimum Jerk Theory (humans minimize jerk/acceleration changes)
- Momentum and inertia (finger can't stop instantly)
- Bezier curves through key centers (smooth arcs, not straight lines)
- Velocity profiles (accelerate-cruise-decelerate)
- Natural variation (not random noise, but physics-based perturbations)

Output format compatible with neural-swipe-typing trainer.
"""

import json
import math
import random
import numpy as np
from dataclasses import dataclass
from typing import List, Tuple, Optional
import argparse

# =============================================================================
# KEYBOARD LAYOUT (Normalized 0-1 coordinates)
# =============================================================================

# Standard QWERTY layout - key centers in normalized space
# These match the FUTO dataset normalization
QWERTY_LAYOUT = {
    # Top row
    'q': (0.05, 0.167), 'w': (0.15, 0.167), 'e': (0.25, 0.167), 'r': (0.35, 0.167),
    't': (0.45, 0.167), 'y': (0.55, 0.167), 'u': (0.65, 0.167), 'i': (0.75, 0.167),
    'o': (0.85, 0.167), 'p': (0.95, 0.167),
    
    # Middle row (slight offset)
    'a': (0.075, 0.50), 's': (0.175, 0.50), 'd': (0.275, 0.50), 'f': (0.375, 0.50),
    'g': (0.475, 0.50), 'h': (0.575, 0.50), 'j': (0.675, 0.50), 'k': (0.775, 0.50),
    'l': (0.875, 0.50),
    
    # Bottom row (more offset)
    'z': (0.15, 0.833), 'x': (0.25, 0.833), 'c': (0.35, 0.833), 'v': (0.45, 0.833),
    'b': (0.55, 0.833), 'n': (0.65, 0.833), 'm': (0.75, 0.833),
}

# Key size for calculating "hit zones"
KEY_WIDTH = 0.10
KEY_HEIGHT = 0.25

# =============================================================================
# PHYSICS PARAMETERS
# =============================================================================

@dataclass
class FingerPhysics:
    """Parameters controlling the simulated finger behavior.
    
    CALIBRATED against FUTO dataset (Dec 2024):
    - 3-letter words should be ~400-600ms
    - Overshoot is SMALL (~0.05 normalized units)
    - Average velocity is ~2 normalized units/sec
    - Humans are actually quite accurate (small target noise)
    """
    
    # Mass affects momentum - higher mass = more overshoot on direction changes
    mass: float = 1.0
    
    # Damping affects how quickly the finger settles - higher = less oscillation
    damping: float = 0.6
    
    # Spring constant for target attraction - higher = snappier movement
    # Much higher to achieve FUTO's fast velocity (~2 units/sec)
    spring_k: float = 80.0
    
    # Maximum velocity (normalized units per second)
    # FUTO shows avg ~2 units/sec, max ~7 units/sec
    max_velocity: float = 10.0
    
    # Minimum jerk coefficient - affects smoothness of trajectory
    jerk_smoothing: float = 0.3
    
    # How much the finger overshoots targets (normalized units)
    # FUTO shows only ~0.05 overshoot - much less than we thought!
    overshoot_factor: float = 0.05
    
    # Noise in the target position (simulates imprecise aiming)
    # FUTO shows small noise - humans are actually quite accurate
    target_noise: float = 0.02
    
    # Time step for physics simulation
    dt: float = 0.001  # 1ms steps (very fast simulation)

# =============================================================================
# MINIMUM JERK TRAJECTORY
# =============================================================================

def minimum_jerk_trajectory(start: np.ndarray, end: np.ndarray, 
                            num_points: int = 50) -> np.ndarray:
    """
    Generate a minimum-jerk trajectory between two points.
    
    This is based on Flash & Hogan (1985) - the trajectory that minimizes
    the integral of jerk (derivative of acceleration) over time.
    
    Humans naturally produce movements that follow this profile.
    """
    t = np.linspace(0, 1, num_points)
    
    # Minimum jerk polynomial: 10t³ - 15t⁴ + 6t⁵
    s = 10 * t**3 - 15 * t**4 + 6 * t**5
    
    # Interpolate between start and end
    trajectory = np.outer(1 - s, start) + np.outer(s, end)
    
    return trajectory

def bezier_curve(control_points: List[np.ndarray], num_points: int = 50) -> np.ndarray:
    """
    Generate a Bezier curve through control points.
    
    For swipe typing, we use cubic Bezier curves to create smooth
    arcs between keys, rather than straight lines.
    """
    n = len(control_points) - 1
    t = np.linspace(0, 1, num_points)
    
    # Bernstein polynomial basis
    def bernstein(i, n, t):
        from math import comb
        return comb(n, i) * (t ** i) * ((1 - t) ** (n - i))
    
    curve = np.zeros((num_points, 2))
    for i, cp in enumerate(control_points):
        curve += np.outer(bernstein(i, n, t), cp)
    
    return curve

# =============================================================================
# PHYSICS SIMULATION
# =============================================================================

class FingerSimulator:
    """
    Simulates a finger moving across a keyboard using spring-mass-damper physics.
    
    The finger is attracted to target keys but has momentum, so it overshoots
    and produces natural-looking curved trajectories.
    """
    
    def __init__(self, physics: FingerPhysics = None):
        self.physics = physics or FingerPhysics()
        
    def calculate_curvature(self, points: np.ndarray) -> np.ndarray:
        """
        Calculate curvature at each point of a trajectory.
        Used for Two-Thirds Power Law velocity modulation.
        
        Curvature = |x'y'' - y'x''| / (x'^2 + y'^2)^(3/2)
        """
        if len(points) < 3:
            return np.ones(len(points))
        
        # First derivatives (velocity)
        dx = np.gradient(points[:, 0])
        dy = np.gradient(points[:, 1])
        
        # Second derivatives (acceleration)
        ddx = np.gradient(dx)
        ddy = np.gradient(dy)
        
        # Curvature formula
        numerator = np.abs(dx * ddy - dy * ddx)
        denominator = (dx**2 + dy**2)**(3/2) + 1e-10  # Avoid division by zero
        
        curvature = numerator / denominator
        return curvature

    def simulate_swipe(self, key_sequence: List[str], 
                       variation_seed: int = None) -> Tuple[List[float], List[float], List[int]]:
        """
        Simulate a finger swiping through a sequence of keys.
        
        Uses Two-Thirds Power Law: velocity ∝ curvature^(-1/3)
        - Sharp turns (high curvature) → slow down
        - Straight paths (low curvature) → speed up
        - Key targets → slight pause (Fitts's Law)
        
        Returns:
            (x_coords, y_coords, timestamps) in FUTO-compatible format
        """
        if variation_seed is not None:
            random.seed(variation_seed)
            np.random.seed(variation_seed)
        
        # Get key positions (with slight noise for natural variation)
        targets = []
        for key in key_sequence:
            if key.lower() in QWERTY_LAYOUT:
                base_pos = np.array(QWERTY_LAYOUT[key.lower()])
                # Add small targeting noise (FUTO shows ~0.02 deviation)
                noise = np.random.normal(0, self.physics.target_noise, 2)
                targets.append(base_pos + noise)
        
        if len(targets) < 2:
            if targets:
                return [targets[0][0]], [targets[0][1]], [0]
            return [], [], []
        
        # SIMPLE APPROACH: Generate minimum-jerk trajectory between each key pair
        # with FUTO-calibrated timing (avg ~15-20ms between points, ~2 units/sec velocity)
        
        all_points = []
        all_times = []
        current_time = 0.0
        
        for i in range(len(targets) - 1):
            start = targets[i]
            end = targets[i + 1]
            distance = np.linalg.norm(end - start)
            
            # FUTO shows ~1.7 units/sec average velocity
            target_velocity = 1.5 * random.uniform(0.9, 1.1)
            segment_duration = distance / target_velocity  # In seconds
            
            # FUTO has ~15-20ms between points  
            avg_dt = 0.016 * random.uniform(0.9, 1.1)
            num_points = max(4, int(segment_duration / avg_dt))
            
            # Generate minimum-jerk trajectory for this segment
            trajectory = minimum_jerk_trajectory(start, end, num_points=num_points)
            
            # Generate time points with velocity profile
            for j, pos in enumerate(trajectory):
                # Skip first point of segments after the first (avoid duplicates)
                if i > 0 and j == 0:
                    continue
                
                all_points.append(pos)
                all_times.append(current_time)
                
                # Velocity profile: slightly slower at edges, faster in middle
                t_norm = j / max(1, num_points - 1)
                velocity_factor = 0.7 + 0.3 * (4 * t_norm * (1 - t_norm))
                dt = avg_dt / velocity_factor
                current_time += dt
            
            # Add small overshoot at corners (middle keys only)
            if i < len(targets) - 2:
                overshoot_amount = self.physics.overshoot_factor * random.uniform(0.8, 1.2)
                overshoot_dir = (end - start) / (distance + 1e-6)
                overshoot_pos = end + overshoot_dir * overshoot_amount
                all_points.append(overshoot_pos)
                all_times.append(current_time)
                current_time += avg_dt * 0.8  # Brief pause at corner
        
        # Convert to lists
        x_coords = [float(p[0]) for p in all_points]
        y_coords = [float(p[1]) for p in all_points]
        timestamps = [int(t * 1000) for t in all_times]  # Convert to milliseconds
        
        # Resample to ~30-50 points (similar to FUTO)
        target_num_points = random.randint(30, 50)
        if len(x_coords) > target_num_points:
            indices = np.linspace(0, len(x_coords) - 1, target_num_points, dtype=int)
            x_coords = [x_coords[i] for i in indices]
            y_coords = [y_coords[i] for i in indices]
            timestamps = [timestamps[i] for i in indices]
        
        return x_coords, y_coords, timestamps

# =============================================================================
# WORD TO SWIPE CONVERTER
# =============================================================================

def word_to_key_sequence(word: str) -> List[str]:
    """
    Convert a word to the sequence of keys that would be swiped.
    
    - Ignores punctuation (apostrophes, etc.)
    - Only includes characters that exist on the keyboard
    - Collapses repeated characters (you don't swipe 'l' twice for 'hello')
    """
    keys = []
    prev_key = None
    
    for char in word.lower():
        if char in QWERTY_LAYOUT:
            # Skip if same as previous (no double-tap in swipe)
            if char != prev_key:
                keys.append(char)
                prev_key = char
    
    return keys

def generate_synthetic_swipes(word: str, num_variations: int = 10,
                              physics: FingerPhysics = None) -> List[dict]:
    """
    Generate multiple synthetic swipe samples for a word.
    
    Returns list of dicts in FUTO-compatible format.
    """
    simulator = FingerSimulator(physics)
    keys = word_to_key_sequence(word)
    
    if len(keys) < 2:
        # Can't swipe a single letter
        return []
    
    samples = []
    for i in range(num_variations):
        # Use different physics variations for diversity
        varied_physics = FingerPhysics(
            mass=physics.mass if physics else 1.0 * random.uniform(0.8, 1.2),
            damping=physics.damping if physics else 0.7 * random.uniform(0.8, 1.2),
            spring_k=physics.spring_k if physics else 15.0 * random.uniform(0.9, 1.1),
            overshoot_factor=random.uniform(0.1, 0.25),
            target_noise=random.uniform(0.01, 0.04),
        )
        
        sim = FingerSimulator(varied_physics)
        x, y, t = sim.simulate_swipe(keys, variation_seed=i * 1000 + hash(word) % 1000)
        
        if x:  # Only add if we got valid data
            samples.append({
                "word": word,
                "curve": {
                    "x": x,
                    "y": y,
                    "t": t,
                    "grid_name": "qwerty_en"
                }
            })
    
    return samples

# =============================================================================
# MAIN ENTRY POINT
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description="Generate synthetic swipe data")
    parser.add_argument("--words", type=str, required=True,
                        help="Path to file containing words (one per line)")
    parser.add_argument("--output", type=str, default="synthetic_swipes.jsonl",
                        help="Output JSONL file path")
    parser.add_argument("--variations", type=int, default=10,
                        help="Number of variations per word")
    parser.add_argument("--preview", action="store_true",
                        help="Preview first 5 words only")
    
    args = parser.parse_args()
    
    # Load words
    print(f"Loading words from {args.words}...")
    words = []
    with open(args.words, 'r') as f:
        for line in f:
            line = line.strip()
            # Skip comments and empty lines
            if line and not line.startswith('#'):
                words.append(line)
    
    print(f"Loaded {len(words)} words")
    
    if args.preview:
        words = words[:5]
        print(f"Preview mode: processing first 5 words only")
    
    # Generate swipes
    print(f"Generating {args.variations} variations per word...")
    all_samples = []
    
    for i, word in enumerate(words):
        if i % 50 == 0:
            print(f"  Processing {i+1}/{len(words)}: {word}")
        
        samples = generate_synthetic_swipes(word, num_variations=args.variations)
        all_samples.extend(samples)
    
    # Write output
    print(f"\nWriting {len(all_samples)} samples to {args.output}...")
    with open(args.output, 'w') as f:
        for sample in all_samples:
            f.write(json.dumps(sample) + '\n')
    
    print(f"Done! Generated {len(all_samples)} synthetic swipe samples")
    print(f"\nSample output for '{words[0]}':")
    if all_samples:
        sample = all_samples[0]
        print(f"  Word: {sample['word']}")
        print(f"  Points: {len(sample['curve']['x'])}")
        print(f"  Duration: {sample['curve']['t'][-1]}ms")
        print(f"  X range: {min(sample['curve']['x']):.3f} - {max(sample['curve']['x']):.3f}")
        print(f"  Y range: {min(sample['curve']['y']):.3f} - {max(sample['curve']['y']):.3f}")

if __name__ == "__main__":
    main()
