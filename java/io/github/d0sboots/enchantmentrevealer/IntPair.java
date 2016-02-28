/* Copyright 2016 David Walker

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package io.github.d0sboots.enchantmentrevealer;

public class IntPair implements Comparable<IntPair> {
    public int first;
    public int second;

    public IntPair() {
    }

    public IntPair(int first, int second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        return first * 119 + second;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof IntPair))
            return false;
        IntPair other = (IntPair) obj;
        return first == other.first && second == other.second;
    }

    @Override
    public int compareTo(IntPair other) {
        if (first != other.first)
            return first - other.first;
        return second - other.second;
    }

    @Override
    public String toString() {
        return "IntPair(" + first + "," + second + ")";
    }
}
