package com.github.cimsbioko.sidecar;

import com.github.batkinson.jrsync.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

@Component
public class FileSystem {

    @Value("${app.data.dir}")
    private Path dataDir;

    public File getContent(Campaign campaign) {
        return dataDir.resolve(getContentFilename(campaign)).toFile();
    }

    public String getContentFilename(Campaign campaign) {
        return campaign.getUuid() + ".db";
    }

    public File getMetadata(Campaign campaign) {
        return dataDir.resolve(getMetadataFilename(campaign)).toFile();
    }

    public String getMetadataFilename(Campaign campaign) {
        return campaign.getUuid() + ".db." + Metadata.FILE_EXT;
    }
}
