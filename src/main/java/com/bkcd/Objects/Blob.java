package com.bkcd.Objects;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;

public class Blob implements ObjectEntity{
    private String data = null;
    private String id = null;

    private final String MODE;
    private final String TYPE = "blob";

    public Blob(String fileData) {
        this.data = fileData;
        this.MODE = null;
    }

    //This constructor is used during tree construction
    public Blob(String blobId, String mode) {
        this.id = new String(blobId);
        this.MODE = mode;
    }

    public byte[] getContent() {
        byte[] content = ("blob " + this.data.getBytes(StandardCharsets.US_ASCII).length + "\0" + this.data).getBytes(StandardCharsets.US_ASCII);

        if(this.id == null) {
            setId(content);
        }
        return content;
    }

    private void setId(byte[] content) {
        this.id = DigestUtils.sha1Hex(content);
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.TYPE;
    }

    public String getMode() {
        return this.MODE;
    }
}
