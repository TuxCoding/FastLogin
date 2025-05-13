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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This limits the number of threads that are used at maximum. Thread creation can be very heavy for the CPU and
 * context switching between threads too. However, we need many threads for blocking HTTP and database calls.
 * Nevertheless, this number can be further limited, because the number of actually working database threads
 * is limited by the size of our database pool. The goal is to separate concerns into processing and blocking only
 * threads.
 */
public class AsyncScheduler extends AbstractAsyncScheduler {

    public AsyncScheduler(Logger logger, Executor processingPool) {
        super(logger, Executors.newVirtualThreadPerTaskExecutor());

        logger.info("Using optimized green threads with Java 21");
    }
}
