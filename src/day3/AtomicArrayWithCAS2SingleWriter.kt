@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val state = array[index]
        if (state is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (state.status.get() != SUCCESS) {
                if (index == state.index1) {
                    //array.compareAndSet(index, state, state.expected1)
                    return state.expected1 as E
                }
                if (index == state.index2) {
                    //array.compareAndSet(index, state, state.expected2)
                    return state.expected2 as E
                }
            } else {
                if (index == state.index1) {
                    //array.compareAndSet(index, state, state.update1)
                    return state.update1 as E
                }
                if (index == state.index2) {
                    //array.compareAndSet(index, state, state.update2)
                    return state.update2 as E
                }
            }
        }

        return state as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
            installDescriptor()
            if (status.get() == SUCCESS) {
                if (array.compareAndSet(index1, this, update1)) {
                    if (array.compareAndSet(index2, this, update2)) {

                    }

                }

            } else if (status.get() == FAILED) {
                if (array.compareAndSet(index1, this, expected1)) {
                    if (array.compareAndSet(index2, this, expected2)) {

                    }

                }
            }
        }

        private fun installDescriptor() {
            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    //array.compareAndSet(index1, this, expected1)
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}