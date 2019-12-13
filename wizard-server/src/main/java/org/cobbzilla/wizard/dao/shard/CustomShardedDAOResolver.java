package org.cobbzilla.wizard.dao.shard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.CustomBeanResolver;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.Set;

@AllArgsConstructor @Slf4j
class CustomShardedDAOResolver implements CustomBeanResolver {

    private ApplicationContext parentContext;

    @Override public Object resolve(DefaultListableBeanFactory defaultListableBeanFactory,
                                    DependencyDescriptor descriptor,
                                    String beanName,
                                    Set<String> autowiredBeanNames,
                                    TypeConverter typeConverter) {

        // shard-specific ApplicationContext must re-use ShardedDAOs from parent
        // otherwise if a SingleShardDAO calls into a ShardedDAO, it would create another level
        // of nested ApplicationContext (which could continue on forever)
        if (AbstractShardedDAO.class.isAssignableFrom(descriptor.getDependencyType())) {
            return parentContext.getBean(descriptor.getDependencyType());
        }
        return null;
    }
}
