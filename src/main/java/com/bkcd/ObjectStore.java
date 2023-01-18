package com.bkcd;

import com.bkcd.Objects.ObjectEntity;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;

//
public class ObjectStore {
    private final Path path;

    public ObjectStore(Path path){
        this.path = path;
    }

    public void store(ObjectEntity obj) throws IOException {
        byte[] content = obj.getContent();
        String id = obj.getId();

        Path objectPath = Path.of(path.toString(), id.substring(0,2), id.substring(2));

        //Prevent overwrite if same object already exists
        if (Files.exists(objectPath)) return;

        Files.createDirectories(objectPath.getParent());

        OutputStream compressionStream = new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(objectPath.toFile())));
        compressionStream.write(content);
        compressionStream.close();
    }
}
