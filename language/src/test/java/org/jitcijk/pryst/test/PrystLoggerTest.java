/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jitcijk.pryst.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PrystLoggerTest {

    private static final Source ADD_PRYST;
    private static final Source MUL_PRYST;
    static {
        ADD_PRYST = Source.newBuilder("pryst", "function add(a,b) {return a + b;} function main() {return add(1,1);}", "add.pst").buildLiteral();
        MUL_PRYST = Source.newBuilder("pryst", "function mul(a,b) {return a * b;} function main() {return mul(1,1);}", "mul.pst").buildLiteral();
    }

    private TestHandler testHandler;
    private Context currentContext;

    @Before
    public void setUp() {
        testHandler = new TestHandler();
    }

    @After
    public void tearDown() {
        if (currentContext != null) {
            currentContext.close();
            currentContext = null;
        }
    }

    private Context createContext(Map<String, String> options) {
        if (currentContext != null) {
            throw new IllegalStateException("Context already created");
        }
        currentContext = Context.newBuilder("pryst").options(options).logHandler(testHandler).build();
        return currentContext;
    }

    @Test
    public void testLoggerNoConfig() {
        final Context context = createContext(Collections.emptyMap());
        executePrystScript(context);
        Assert.assertTrue(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerPrystFunctionLevelFine() {
        final Context context = createContext(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE"));
        executePrystScript(context);
        Assert.assertFalse(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerPrystFunctionParentLevelFine() {
        final Context context = createContext(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime", "FINE"));
        executePrystScript(context);
        Assert.assertFalse(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerPrystFunctionSiblingLevelFine() {
        final Context context = createContext(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystContext", "FINE"));
        executePrystScript(context);
        Assert.assertTrue(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testMultipleContextsExclusiveFineLevel() {
        final TestHandler handler1 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler1).build()) {
            executePrystScript(ctx, ADD_PRYST, 2);
        }
        final TestHandler handler2 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler2).build()) {
            executePrystScript(ctx, MUL_PRYST, 1);
        }
        final TestHandler handler3 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler3).build()) {
            executePrystScript(ctx, ADD_PRYST, 2);
        }
        Set<String> functionNames = functionNames(handler1.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
        functionNames = functionNames(handler2.getRecords());
        Assert.assertFalse(functionNames.contains("add"));
        Assert.assertTrue(functionNames.contains("mul"));
        functionNames = functionNames(handler3.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
    }

    @Test
    public void testMultipleContextsExclusiveDifferentLogLevel() {
        final TestHandler handler1 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler1).build()) {
            executePrystScript(ctx, ADD_PRYST, 2);
        }
        final TestHandler handler2 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").logHandler(handler2).build()) {
            executePrystScript(ctx, MUL_PRYST, 1);
        }
        final TestHandler handler3 = new TestHandler();
        try (Context ctx = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler3).build()) {
            executePrystScript(ctx, ADD_PRYST, 2);
        }
        Set<String> functionNames = functionNames(handler1.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
        functionNames = functionNames(handler2.getRecords());
        Assert.assertTrue(functionNames.isEmpty());
        functionNames = functionNames(handler3.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
    }

    @Test
    public void testMultipleContextsNestedFineLevel() {
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler1).build()) {
            try (Context ctx2 = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler2).build()) {
                try (Context ctx3 = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler3).build()) {
                    executePrystScript(ctx1, ADD_PRYST, 2);
                    executePrystScript(ctx2, MUL_PRYST, 1);
                    executePrystScript(ctx3, ADD_PRYST, 2);
                }
            }
        }
        Set<String> functionNames = functionNames(handler1.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
        functionNames = functionNames(handler2.getRecords());
        Assert.assertFalse(functionNames.contains("add"));
        Assert.assertTrue(functionNames.contains("mul"));
        functionNames = functionNames(handler3.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
    }

    @Test
    public void testMultipleContextsNestedDifferentLogLevel() {
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler1).build()) {
            try (Context ctx2 = Context.newBuilder("pryst").options(createLoggingOptions("pryst", "org.jitcijk.pryst.runtime.PrystFunction", "FINE")).logHandler(handler2).build()) {
                try (Context ctx3 = Context.newBuilder("pryst").logHandler(handler3).build()) {
                    executePrystScript(ctx1, ADD_PRYST, 2);
                    executePrystScript(ctx2, MUL_PRYST, 1);
                    executePrystScript(ctx3, ADD_PRYST, 2);
                }
            }
        }
        Set<String> functionNames = functionNames(handler1.getRecords());
        Assert.assertTrue(functionNames.contains("add"));
        Assert.assertFalse(functionNames.contains("mul"));
        functionNames = functionNames(handler2.getRecords());
        Assert.assertFalse(functionNames.contains("add"));
        Assert.assertTrue(functionNames.contains("mul"));
        functionNames = functionNames(handler3.getRecords());
        Assert.assertTrue(functionNames.isEmpty());
    }

    private static void executePrystScript(final Context context) {
        executePrystScript(context, ADD_PRYST, 2);
    }

    private static void executePrystScript(final Context context, final Source src, final int expectedResult) {
        final Value res = context.eval(src);
        Assert.assertTrue(res.isNumber());
        Assert.assertEquals(expectedResult, res.asInt());
    }

    private static Map<String, String> createLoggingOptions(String... kvs) {
        if ((kvs.length % 3) != 0) {
            throw new IllegalArgumentException("Lang, Key, Val length has to be divisible by 3.");
        }
        final Map<String, String> options = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 3) {
            options.put(String.format("log.%s.%s.level", kvs[i], kvs[i + 1]), kvs[i + 2]);
        }
        return options;
    }

    private static Set<String> functionNames(final List<? extends LogRecord> records) {
        return records.stream().filter((lr) -> "pryst.org.jitcijk.pryst.runtime.PrystFunction".equals(lr.getLoggerName())).map((lr) -> (String) lr.getParameters()[0]).collect(Collectors.toSet());
    }

    private static final class TestHandler extends Handler {
        private final Queue<LogRecord> records;
        private volatile boolean closed;

        TestHandler() {
            this.records = new ArrayDeque<>();
        }

        @Override
        public void publish(LogRecord record) {
            if (closed) {
                throw new IllegalStateException("Closed handler");
            }
            records.offer(record);
        }

        @Override
        public void flush() {
            if (closed) {
                throw new IllegalStateException("Closed handler");
            }
        }

        public List<? extends LogRecord> getRecords() {
            return new ArrayList<>(records);
        }

        @Override
        public void close() throws SecurityException {
            closed = true;
        }
    }
}
