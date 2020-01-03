package org.cobbzilla.wizard.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

@NoArgsConstructor @Accessors(chain=true)
public class ClasspathScanner<T> {

    @Getter @Setter private TypeFilter filter;
    @Getter @Setter private String[] packages;

    public List<Class<? extends T>> scan() {
        final List<Class<? extends T>> classes = new ArrayList<>();
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(filter);
        Arrays.stream(packages)
                .forEach(pkg -> scanner.findCandidateComponents(pkg)
                        .forEach(def -> classes.add(forName(def.getBeanClassName()))));
        return classes;
    }

    public static <C> List<Class<? extends C>> scan(Class<C> iface, String[] packages) {
        return new ClasspathScanner<C>()
                .setFilter(new AssignableTypeFilter(iface))
                .setPackages(packages)
                .scan();
    }

}
