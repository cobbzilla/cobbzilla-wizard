package org.cobbzilla.wizardtest;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.reflect.ClassReLoader;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import java.util.Collection;

@Slf4j
public abstract class SeparateClassloaderTestRunner extends BlockJUnit4ClassRunner {

    public SeparateClassloaderTestRunner(Class<?> clazz, Collection<String> toReload) throws InitializationError {
        super(getFromClassLoader(clazz, toReload));
    }

    public SeparateClassloaderTestRunner(Class<?> clazz, String toReload) throws InitializationError {
        this(clazz, new SingletonList<>(toReload));
    }

    private static Class<?> getFromClassLoader(Class<?> clazz, Collection<String> toReload) throws InitializationError {
        try {
            final ClassLoader testClassLoader = new ClassReLoader(toReload);
            return Class.forName(clazz.getName(), true, testClassLoader);
        } catch (ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }

}