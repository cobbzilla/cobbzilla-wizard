package org.cobbzilla.wizard.resources;

import org.cobbzilla.wizard.asset.AssetStorageService;
import org.cobbzilla.wizard.asset.AssetStream;
import org.cobbzilla.wizard.model.AssetStorageInfo;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.contentType;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public abstract class FileUploadResource<FI extends AssetStorageInfo> {

    protected abstract boolean validateUpload(ContainerRequest context);
    protected abstract boolean validateDownload(ContainerRequest context, String uuid);
    protected abstract boolean validateDelete(ContainerRequest context, String uuid);

    /**
     * Subclasses should override this method, or subclass a from a more specific class like PdfUploadResource
     * @return Regular expression representing the allowed file extensions
     */
    protected abstract Pattern getAllowedFileExtensions();

    protected abstract AssetStorageService getStorage();

    protected abstract FI createFileInfo(FormDataContentDisposition fileDetail);
    protected abstract Collection<FI> findFileInfos();
    protected abstract FI findFileInfo(String uuid);
    protected abstract void deleteFileInfo(FI fileInfo);

    @GET
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response findDocuments(@Context ContainerRequest context) {
        if (!validateDownload(context, null)) return forbidden();
        return ok(findFileInfos());
    }

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    public Response uploadDocument(@Context ContainerRequest context,
                                   @FormDataParam("file") InputStream fileStream,
                                   @FormDataParam("file") FormDataContentDisposition fileDetail) {

        if (!validateUpload(context)) return forbidden();

        final Optional<String> error = validateFile(fileStream, fileDetail);
        if (error.isPresent()) return invalid(error.get());

        final FI fileInfo = createFileInfo(fileDetail);
        getStorage().store(fileStream, fileDetail.getFileName(), fileInfo.getAsset());
        return ok(fileInfo);
    }

    @GET @Path("/{uuid}")
    @Consumes(APPLICATION_JSON)
    @Produces("*/*")
    public Response downloadDocument(@Context ContainerRequest context,
                                     @PathParam("uuid") String uuid) {

        if (!validateDownload(context, uuid)) return forbidden();

        final FI fileInfo = findFileInfo(uuid);
        if (fileInfo == null) return notFound(uuid);

        final AssetStream assetStream = getStorage().load(fileInfo.getAsset());
        if (empty(assetStream)) return notFound();

        return stream(contentType(assetStream.getFormatName()), assetStream.getStream());
    }

    @DELETE @Path("/{uuid}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteDocument(@Context ContainerRequest context,
                                   @PathParam("uuid") String uuid) {

        if (!validateDelete(context, uuid)) return forbidden();

        final FI fileInfo = findFileInfo(uuid);
        if (fileInfo == null) return notFound(uuid);

        if (getStorage().exists(fileInfo.getAsset())) {
            if (!getStorage().delete(fileInfo.getAsset())) return die("deleteDocument: could not delete: " + fileInfo.getAsset());
        }
        deleteFileInfo(fileInfo);

        return ok(fileInfo);
    }

    private Optional<String> validateFile(InputStream fileStream, FormDataContentDisposition fileDetail) {
        if (fileStream == null) return Optional.of("err.fileStream.empty");
        if (fileDetail == null) return Optional.of("err.fileDetail.empty");
        if (empty(fileDetail.getFileName())) return Optional.of("err.fileName.empty");
        if (!getAllowedFileExtensions().matcher(fileDetail.getFileName()).matches()) {
            return Optional.of("err.fileFormat.invalid");
        }

        return Optional.empty();
    }
}
