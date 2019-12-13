package org.cobbzilla.wizard.util;

import lombok.Cleanup;
import org.apache.commons.io.IOUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

public class SpringUtil {

    public static final ClassLoader DEFAULT_CLASS_LOADER = SpringUtil.class.getClassLoader();

    public static <T> T autowire(ApplicationContext ctx, T bean) {
        ctx.getAutowireCapableBeanFactory().autowireBean(bean);
        return bean;
    }

    public static Resource[] listResources(String pattern) throws Exception {
        return listResources(pattern, DEFAULT_CLASS_LOADER);
    }
    public static Resource[] listResources(String pattern, ClassLoader loader) throws Exception {
        return new PathMatchingResourcePatternResolver(loader).getResources(pattern);
    }

    public static Resource uniqueResource(String pattern) throws Exception {
        final Resource[] resources = listResources(pattern, DEFAULT_CLASS_LOADER);
        return empty(resources) ? null : resources.length == 1 ? resources[0] : die("uniqueResource: "+resources.length+" resources found for "+pattern);
    }

    public static Resource uniqueRequiredResource(String pattern) {
        try {
            final Resource r = uniqueResource(pattern);
            if (r == null) return die("uniqueRequiredResource: resource not found: "+pattern);
            return r;

        } catch (Exception e) {
            return die("uniqueRequiredResource: "+e, e);
        }
    }

    public static Resource[] findResources(String pattern, Predicate<Resource> predicate) throws Exception {
        return findResources(pattern, DEFAULT_CLASS_LOADER, predicate);
    }
    public static Resource[] findResources(String pattern, ClassLoader loader, Predicate predicate) throws Exception {
        final Resource[] resources = listResources(pattern, loader);
        return empty(resources) ? resources : (Resource[]) Arrays.stream(resources).filter(predicate).toArray();
    }

    public static void copyResources(String pattern, File dir) throws Exception {
        for (Resource r : listResources(pattern)) {
            final File temp = new File(dir, r.getFilename());
            @Cleanup final InputStream in = r.getInputStream();
            @Cleanup final OutputStream out = new FileOutputStream(temp);
            IOUtils.copy(in, out);
        }
    }

    public static <T> T getBean (ApplicationContext applicationContext, Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    public static <T> Map<String, T> getBeans (ApplicationContext applicationContext, Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

    public static <T> T getBean (ApplicationContext applicationContext, String clazz) {
        return (T) applicationContext.getBean(forName(clazz));
    }
}
