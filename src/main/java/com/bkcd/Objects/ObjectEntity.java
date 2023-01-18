package com.bkcd.Objects;

import java.io.IOException;

public interface ObjectEntity {
    byte[] getContent() throws IOException;

    /**
     *   Object Id is calculated lazily during the storing process.
     *   Updated Id will be available only after getContent() method has been called
     */
    String getId();
    String getType();
    //Mode only makes sense for trees & blobs since they represent entities in our repo
    String getMode();
}
