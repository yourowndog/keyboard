/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.gestures;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.pytorch.executorch.DType;
import org.pytorch.executorch.Tensor;

/**
 * Two small gaps in ExecuTorch's Android bindings, bridged from Java.
 *
 * <p>The FUTO encoder's {@code layout_mask} input is strictly bool -- the runtime rejects
 * uint8, int8, int32, int64 and float32 alike. {@code Tensor} ships no bool implementation
 * and its {@code Tensor(long[])} constructor is Kotlin-{@code internal}, so Kotlin cannot
 * subclass it; Java can. The JNI layer reads {@code dtypeJniCode()}, {@code shape()} and
 * {@code getRawDataBuffer()} off the object at call time and holds no native handle of its
 * own, so a plain subclass over a direct byte buffer is a valid bool tensor.
 *
 * <p>{@code getDataAsFloatArray} is likewise awkward to reach from Kotlin, so it is
 * forwarded here rather than worked around at every call site.
 */
public final class FutoEtCompat {
    private FutoEtCompat() {}

    private static final class BoolTensor extends Tensor {
        private final ByteBuffer data;

        BoolTensor(ByteBuffer data, long[] shape) {
            super(shape);
            this.data = data;
        }

        @Override
        public DType dtype() {
            return DType.BOOL;
        }

        @Override
        public Buffer getRawDataBuffer() {
            return data;
        }
    }

    /** Builds a bool tensor of {@code shape} whose first {@code trueCount} entries are set. */
    public static Tensor boolTensor(int length, int trueCount, long[] shape) {
        ByteBuffer buf = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        for (int i = 0; i < length; i++) {
            buf.put(i, (byte) (i < trueCount ? 1 : 0));
        }
        return new BoolTensor(buf, shape);
    }

    public static float[] floatData(Tensor tensor) {
        return tensor.getDataAsFloatArray();
    }
}
