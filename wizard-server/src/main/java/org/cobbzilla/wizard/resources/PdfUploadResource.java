package org.cobbzilla.wizard.resources;

import org.cobbzilla.wizard.model.AssetStorageInfo;

import java.util.regex.Pattern;

public abstract class PdfUploadResource<FI extends AssetStorageInfo> extends FileUploadResource<FI> {

    @Override protected Pattern getAllowedFileExtensions() { return Pattern.compile(".*\\.pdf"); }

}
