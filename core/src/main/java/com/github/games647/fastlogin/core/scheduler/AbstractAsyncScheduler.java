/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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
 */
package com.github.games647.fastlogin.core.scheduler;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractAsyncScheduler {

    protected final Logger logger;
    protected final Executor processingPool;
    protected final AtomicInteger currentlyRunning = new AtomicInteger();

    public AbstractAsyncScheduler(Logger logger, Executor processingPool) {
        this.logger = logger;
        this.processingPool = processingPool;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(() -> process(task), processingPool).exceptionally(error -> {
            logger.warn("Error occurred on thread pool", error);
            return null;
        });
    }

    public CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay) {
        return CompletableFuture.runAsync(() -> {
            currentlyRunning.incrementAndGet();
            try {
                Thread.sleep(delay.toMillis());
                task.run();
            } catch (InterruptedException interruptedException) {
                // restore interrupt flag
                Thread.currentThread().interrupt();
            } finally {
                currentlyRunning.getAndDecrement();
            }
        }, processingPool).exceptionally(error -> {
            logger.warn("Error occurred on thread pool", error);
            return null;
        });
    }

    protected void process(Runnable task) {
        currentlyRunning.incrementAndGet();
        try {
            task.run();
        } finally {
            currentlyRunning.getAndDecrement();
        }
    }
}
