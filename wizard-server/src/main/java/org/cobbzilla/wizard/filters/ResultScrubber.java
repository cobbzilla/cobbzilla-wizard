package org.cobbzilla.wizard.filters;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.util.Collection;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.setNull;

@Slf4j
public abstract class ResultScrubber implements ContainerResponseFilter {

    protected abstract ScrubbableField[] getFieldsToScrub(Object entity);

    @Override public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        final Object entity = response.getEntity();
        final ScrubbableField[] fieldsToScrub = getFieldsToScrub(entity);
        if (fieldsToScrub != null && fieldsToScrub.length > 0) scrub(entity, fieldsToScrub);
    }

    public static void scrub(Object entity, ScrubbableField[] fieldsToScrub) {
        if (entity instanceof Collection) {
            for (Scrubbable s : (Collection<Scrubbable>) entity) scrub(s, fieldsToScrub);
        } else if (Scrubbable[].class.isAssignableFrom(entity.getClass())) {
            for (Scrubbable s : (Scrubbable[]) entity) scrub(s, fieldsToScrub);
        } else {
            for (ScrubbableField field : fieldsToScrub) {
                if (entity != null && field.targetType.isAssignableFrom(entity.getClass())) {
                    try {
                        if (entity instanceof CustomScrubbage) {
                            ((CustomScrubbage) entity).scrub(entity, field);
                        } else {
                            boolean recurse = field.name.endsWith(".*");
                            final String fieldName = recurse ? field.name.substring(0, field.name.length() - ".*".length()) : field.name;
                            final Object thing = ReflectionUtil.get(entity, fieldName);
                            if (thing == null) continue;
                            if (!field.type.isAssignableFrom(thing.getClass()))
                                die("scrub: incompatible types: " + thing.getClass().getName() + ", " + field.type.getName());
                            if (recurse) {
                                if (thing instanceof Collection) {
                                    for (Object subThing : (Collection) thing) {
                                        if (subThing instanceof Scrubbable) {
                                            scrub(subThing, ((Scrubbable) subThing).fieldsToScrub());
                                        }
                                    }
                                } else if (thing.getClass().isArray()) {
                                    for (Object subThing : (Object[]) thing) {
                                        if (subThing instanceof Scrubbable) {
                                            scrub(subThing, ((Scrubbable) subThing).fieldsToScrub());
                                        }
                                    }

                                } else {
                                    die("scrub: neither collection nor array: " + fieldName + " (was " + thing.getClass() + ")");
                                }
                            } else {
                                setNull(entity, field.name, field.type);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("filter: Error calling ReflectionUtil.setNull(" + entity + ", " + field.name + ", " + field.type.getName() + "): " + e);
                    }
                }
            }
        }
    }

}
