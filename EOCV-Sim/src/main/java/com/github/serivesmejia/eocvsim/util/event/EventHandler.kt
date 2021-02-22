/*
 * Copyright (c) 2021 Sebastian Erives
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.github.serivesmejia.eocvsim.util.event

import com.github.serivesmejia.eocvsim.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.collections.Map.Entry

class EventHandler(val name: String) : Runnable {

    private val lock = Any()

    val listeners: Array<EventListener>
        get()  {
            synchronized(lock) {
                return internalListeners.values.toTypedArray()
            }
        }

    private val internalListeners = HashMap<Int, EventListener>()

    companion object {
        private var idCount = 0
    }

    override fun run() {
        for(listener in listeners) {
            try {
                listener.run()
            } catch (ex: Exception) {
                Log.error("${name}-EventHandler", "Error while running listener #${listener.id} (${listener.javaClass})", ex)
            }

            if(!listener.persistent) {
                removeListener(listener.id)
            }
        }
    }

    fun doOnce(listener: EventListener): Int = synchronized(lock) {
        internalListeners[idCount + 1] = listener
        listener.id = idCount + 1

        return listener.id
    }

    fun doOnce(runnable: Runnable) = doOnce(KEventListener { runnable.run() })

    fun doOnce(listener: (Int) -> Unit) = doOnce(KEventListener(listener))

    fun doPersistent(listener: EventListener) = synchronized(lock) {
        doOnce(listener)
        listener.persistent = true
    }

    fun doPersistent(runnable: Runnable) = doPersistent(KEventListener { runnable.run() })

    fun doPersistent(listener: (Int) -> Unit) = doPersistent(KEventListener(listener))

    fun getListener(id: Int): EventListener? = internalListeners[id]

    fun getKListener(id: Int): KEventListener? {
        val listener = getListener(id) ?: return null
        return if(listener is KEventListener) { listener } else { null }
    }

    fun removeListener(id: Int) = synchronized(lock) { internalListeners.remove(id) }

}