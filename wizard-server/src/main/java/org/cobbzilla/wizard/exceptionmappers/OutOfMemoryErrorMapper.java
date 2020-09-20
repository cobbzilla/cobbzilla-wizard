package org.cobbzilla.wizard.exceptionmappers;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.system.OutOfMemoryErrorUncaughtExceptionHandler.EXIT_ON_OOME;
import static org.cobbzilla.wizard.resources.ResourceUtil.serverError;

@Slf4j
public class OutOfMemoryErrorMapper implements ExceptionMapper<OutOfMemoryError> {

    @Autowired private RestServerConfiguration configuration;

    @Override public Response toResponse(OutOfMemoryError e) {
        if (configuration.getHttp().isExitOnOutOfMemoryError()) {
            EXIT_ON_OOME.uncaughtException(Thread.currentThread(), e);
        }
        log.error("!!!!! OutOfMemoryError: "+shortError(e), e);
        return serverError();
    }

}
