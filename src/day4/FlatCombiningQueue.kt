package day4

import day1.Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (tryLock()) {
                processLockEnqueue(element)
                return
            }
            var cellIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(cellIndex, null, element)) {
                while (true) {
                    var cellValue = tasksForCombiner.get(cellIndex)
                    when {
                        cellValue is Result<*> -> {
                            tasksForCombiner.compareAndSet(cellIndex, cellValue, null)
                            return
                        }

                        tryLock() -> {
                            cellValue = tasksForCombiner.get(cellIndex)
                            tasksForCombiner.compareAndSet(cellIndex, cellValue, null)
                            if (cellValue is Result<*>) {
                                unlock()
                                return
                            }
                            processLockEnqueue(element)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun processLockEnqueue(element: E) {
        queue.addLast(element)
        helpArray()
        unlock()
    }


    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (tryLock()) {
                return processLockDequeue()
            }
            var cellIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(cellIndex, null, Dequeue)) {
                while (true) {
                    var cellValue = tasksForCombiner.get(cellIndex)
                    when {
                        cellValue is Result<*> -> {
                            tasksForCombiner.compareAndSet(cellIndex, cellValue, null)
                            return cellValue.value as E
                        }

                        tryLock() -> {
                            cellValue = tasksForCombiner.get(cellIndex)
                            tasksForCombiner.compareAndSet(cellIndex, cellValue, null)
                            if (cellValue is Result<*>) {
                                unlock()
                                return cellValue.value as E
                            }
                            return processLockDequeue()
                        }
                    }
                }
            }
        }
    }

    private fun processLockDequeue(): E? {
        val result = queue.removeFirstOrNull()
        helpArray()
        unlock()
        return result
    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.set(false)
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())

    private fun helpArray() {
        for (index in 0 until tasksForCombiner.length()) {
            when (val cell = tasksForCombiner.get(index)) {
                // dequeue
                is Dequeue -> tasksForCombiner.compareAndSet(index, Dequeue, Result(queue.removeFirstOrNull()))
                // already processed, leave for another thread to pick
                is Result<*> -> {}
                null -> {}
                // enqueue request, put result to the queue
                else -> {
                    if (tasksForCombiner.compareAndSet(index, cell, Result(cell))) {
                        queue.addLast(cell as E)
                    }
                }
            }
        }
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)