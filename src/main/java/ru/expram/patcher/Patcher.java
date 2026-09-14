package ru.expram.patcher;

import java.lang.instrument.Instrumentation;

public class Patcher {

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new NexusPatchTransformer());
    }

}
