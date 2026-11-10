package ru.expram.patcher;

import javassist.ClassPool;
import javassist.CtClass;

public interface Patch {

    byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception;
}
